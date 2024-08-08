package com.pasteCard.keyGenerationLambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.pasteCard.keyGenerationLambda.models.GetKeyRequest;
import com.pasteCard.keyGenerationLambda.models.GetKeyResponse;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
// import com.amazonaws.services.dynamodbv2.document.PrimaryKey;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.PutItemSpec;

import java.math.BigInteger;
import java.util.UUID;

public class KeyGenerationLambda implements RequestHandler<GetKeyRequest, GetKeyResponse> {

    private final String DYNAMODB_TABLE_NAME = "pasteCard-key";

    @Override
    public GetKeyResponse handleRequest(GetKeyRequest input, Context context) {

        String ipAddress = input.getIpAddress();
        Long timestamp = input.getTimestamp();

        System.out.println("before TTL : " + input.getTTL());


        Long ttlTimestamp = timestamp + (input.getTTL() != null ? input.getTTL() : 31060000000L); // Calculate TTL here,
                                                                                                  // 2 days
        ttlTimestamp = ttlTimestamp / 1000; // by dfault

        System.out.println("after TTL : " + ttlTimestamp);

        Boolean isAlias = input.getIsAlias();
        String alias = input.getAlias();

        String generatedKey = isAlias ? alias : generateRandomUniqueKey(ipAddress, timestamp);
        // System.out.println("KEY : " + generateUniqueKey(ipAddress, ttlTimestamp));
        if (generatedKey != null) {
            boolean updateSuccess=updateDynamoDB(generatedKey, ttlTimestamp);
            if(!updateSuccess){
                return GetKeyResponse.builder()
                    .key(generatedKey)
                    .isSuccess(false).build();
            }
            return GetKeyResponse.builder()
                    .key(generatedKey)
                    .isSuccess(true).build();
        } else {
            return GetKeyResponse.builder()
                    .key(generatedKey)
                    .isSuccess(false).build();
        }

    }

    // private String generateAliasUniqueKey(String alias) {
    //     return isKeyUnique(alias) ? alias : null;
    // }

    private String generateRandomUniqueKey(String ipAddress, Long timestamp) {
        UUID uuid = UUID.randomUUID();
        String encodedUUID = base62EncodeUUID(uuid);
        Long ipAddressValue = convertIP(ipAddress);
        Long newKey = ipAddressValue + timestamp; // 64 bit long value -> 11 characters in b62 at max
        String encodedNewKey = base62EncodeLong(newKey);
        String finalUniqueID = encodedUUID + encodedNewKey; // 22 + 11 -> 33 characters at max
        return finalUniqueID;
    }

    private Long convertIP(String ipAddress) {
        String[] octets = ipAddress.split("\\.");
        Long ipValue = 0L;
        for (String octet : octets) {
            ipValue = (ipValue << 8) + Long.parseLong(octet);
        }

        return ipValue;
    }

    private String base62EncodeLong(Long input) {
        String base62Chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        StringBuilder result = new StringBuilder();

        while (input > 0) {
            int remainder = (int) (input % 62);
            result.insert(0, base62Chars.charAt(remainder));
            input /= 62;
        }

        if (result.length() == 0) {
            System.out.println("Input newKey is empty");
            result.append('0');
        }

        return result.toString();

    }

    private String base62EncodeUUID(UUID uuid) {
        byte[] bytes = toByteArray(uuid);
        return base62Encode(bytes);
    }

    private String base62Encode(byte[] bytes) {
        BigInteger number = new BigInteger(1, bytes);
        String base62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        StringBuilder result = new StringBuilder();

        while (number.compareTo(BigInteger.ZERO) > 0) {
            BigInteger[] quotientAndRemainder = number.divideAndRemainder(BigInteger.valueOf(62));
            result.insert(0, base62.charAt(quotientAndRemainder[1].intValue()));
            number = quotientAndRemainder[0];
        }

        return result.toString();
    }

    private byte[] toByteArray(UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        byte[] buffer = new byte[16];

        for (int i = 0; i < 8; i++) {
            buffer[i] = (byte) (msb >>> 8 * (7 - i));
        }

        for (int i = 8; i < 16; i++) {
            buffer[i] = (byte) (lsb >>> 8 * (7 - i));
        }

        return buffer;
    }

    // private boolean isKeyUnique(String key) {
    //     AmazonDynamoDB client = AmazonDynamoDBClientBuilder.defaultClient();
    //     DynamoDB dynamoDB = new DynamoDB(client);
    //     Table table = dynamoDB.getTable(DYNAMODB_TABLE_NAME);

    //     Item item = table.getItemOutcome(new PrimaryKey("keyID", key)).getItem();
    //     return item == null;
    // }

    private boolean updateDynamoDB(String key, Long ttlTimestamp) {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.defaultClient();
        DynamoDB dynamoDB = new DynamoDB(client);
        Table table = dynamoDB.getTable(DYNAMODB_TABLE_NAME);
        PutItemSpec putItemSpec = new PutItemSpec()
                .withItem(new Item()
                        .withPrimaryKey("keyID", key)
                        .withLong("TTL", ttlTimestamp))
                .withConditionExpression("attribute_not_exists(keyID)");
        // Item item = new Item()
        // .withPrimaryKey("keyID", key)
        // .withLong("TTL", ttlTimestamp);
        try {
            table.putItem(putItemSpec);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
