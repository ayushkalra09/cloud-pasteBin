package com.pasteCard.fetchLambda;

import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;
import com.amazonaws.services.dynamodbv2.document.spec.PutItemSpec;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;

public class FetchLambda implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final String BUCKET_NAME = "paste-card-bucket";
    private AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
    private final String DYNAMODB_TABLE_NAME = "pasteCard-key";

    private final Map<String, String> headers = new HashMap<>();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        headers.put("Access-Control-Allow-Origin", "*");

        String key = extractKeyFromPath(input.getPath()); // Extract key from the URL path

        System.out.println("key: " + key);

        // Check if the key exists in the bucket
        if (isTTLValid(key)) {
            // Generate the signed URL
            String signedUrl = generatePresignedUrl(key, HttpMethod.GET).toString();
            String body = "{" +
                    "\"url\": \"" + signedUrl + "\"" +
                    "}";
            return new APIGatewayProxyResponseEvent().withBody(body).withHeaders(headers).withStatusCode(200);
        } else {
            String body = "{" +
                    "\"url\": \"KEY_DOES_NOT_EXIST\"" +
                    "}";
            return new APIGatewayProxyResponseEvent().withBody(body).withStatusCode(404).withHeaders(headers);
        }
    }

    private Boolean isTTLValid(String key) {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.defaultClient();
        DynamoDB dynamoDB = new DynamoDB(client);
        Table table = dynamoDB.getTable(DYNAMODB_TABLE_NAME);

        try {
            Item item = table.getItem("keyID", key);
            System.out.println("it : " + item);
            if (item != null) {
                long ttlFromDynamoDB = item.getLong("TTL");
                long currentTime = System.currentTimeMillis() / 1000;
                Long val = 1L;
                try {
                    val = item.getLong("viewCount");
                    val = val + 1L;
                } catch (Exception e) {
                    System.out.println("There was nothing in ViewCount");
                }

                System.out.println("ttlFromDDB : " + ttlFromDynamoDB);
                System.out.println("cuyrTioime" + currentTime);
                System.out.println("viewcount : " + val);

                if (ttlFromDynamoDB < currentTime)
                    return false;

                PutItemSpec putItemSpec = new PutItemSpec()
                        .withItem(new Item()
                                .withPrimaryKey("keyID", key)
                                .withLong("viewCount", val)
                                .withLong("TTL", ttlFromDynamoDB));
                try {
                    table.putItem(putItemSpec);
                    return true;
                } catch (ConditionalCheckFailedException c) {
                    System.out.println("unmarshalling exception : " + c.getMessage());
                    return false;
                } catch (Exception e) {
                    System.out.println("EXCEPTION : " + e.getMessage().toString());
                    return false;
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return false;
    }

    private String extractKeyFromPath(String path) {
        // Extract the key from the URL path assuming the format /apiresource/<key>
        String[] parts = path.split("/");
        if (parts.length >= 2) {
            return parts[2]; // Assuming the key is at index 2
        }
        return ""; // Return empty string if key not found in path
    }

    private URL generatePresignedUrl(String s3ObjectKey, HttpMethod httpMethod) {
        Date expiration = new Date(System.currentTimeMillis() + 3600000); // 1 hour

        GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(BUCKET_NAME,
                s3ObjectKey)
                .withMethod(httpMethod)
                .withExpiration(expiration);

        return s3Client.generatePresignedUrl(generatePresignedUrlRequest);
    }
}
