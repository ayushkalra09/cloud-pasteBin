package com.pasteCard.shortenLink;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.PutItemSpec;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.pasteCard.shortenLink.models.ShortURLRequest;
import com.pasteCard.shortenLink.models.ShortURLResponse;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ShortenLinkCreate implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String DOMAIN_NAME = "https://yg8x4tc026.execute-api.ap-south-1.amazonaws.com/prod/s";
    private static final String TABLE_NAME = "pasteCard-short";

    private final Map<String, String> headers = new HashMap<>();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        headers.put("Access-Control-Allow-Origin", "*");

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();

        try {
            ShortURLRequest sur = fromJsonString(input.getBody());
            String longURL = sur.getLongURL();
            String key = generateKey(longURL);
            String shortURL = DOMAIN_NAME + "/" + key;


            System.out.println("Key generated :" + key);

            // Store the mapping of longURL to key in DynamoDB
            Boolean saveResponseSuccess = saveToDynamoDB(longURL, key);


            if (saveResponseSuccess == false) {
                ShortURLResponse outputObject = new ShortURLResponse();
                outputObject.setShortURL("Save to DDB failed");

                response.setStatusCode(409);
                response.setBody(toJsonString(outputObject));
                response.withHeaders(headers);

            } else {
                ShortURLResponse outputObject = new ShortURLResponse();
                outputObject.setShortURL(shortURL);

                response.setStatusCode(200);
                response.setBody(toJsonString(outputObject));
                response.withHeaders(headers);
            }

        } catch (Exception e) {
            response.setStatusCode(500);
            response.setBody("{\"Error\":\"" + e.getMessage() + "\"}");
            response.withHeaders(headers);
        }

        return response;
    }

    public String toJsonString(ShortURLResponse object) {
        Gson gson = new Gson();
        return gson.toJson(object);
    }

    public ShortURLRequest fromJsonString(String s){
         Gson gson = new Gson();
        return gson.fromJson(s, ShortURLRequest.class);
    }

    private String generateKey(String longURL) throws NoSuchAlgorithmException {
        String[] algorithms = { "MD5", "SHA-1", "SHA-256" };
        Random random = new Random();
        String algorithm = algorithms[random.nextInt(algorithms.length)];

        MessageDigest md = MessageDigest.getInstance(algorithm);
        md.update(longURL.getBytes());
        byte[] digest = md.digest();


        ArrayList<Integer> indices = new ArrayList<>();
        for (int i = 0; i < digest.length; i++) {
            indices.add(i);
        }

        Collections.shuffle(indices);

        int length = digest.length < 3 ? digest.length : 3;

        StringBuilder keyBuilder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int index = indices.get(i);
            keyBuilder.append(Integer.toString((digest[index] & 0xff) + 0x100, 16).substring(1));
        }

        return keyBuilder.toString();
    }

    private Boolean saveToDynamoDB(String longURL, String key) {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.defaultClient();
        DynamoDB dynamoDB = new DynamoDB(client);
        Table table = dynamoDB.getTable(TABLE_NAME);
        PutItemSpec putItemSpec = new PutItemSpec()
                .withItem(new Item()
                        .withPrimaryKey("keyID", key)
                        .withString("longURL", longURL))
                .withConditionExpression("attribute_not_exists(keyID)");
        System.out.println(putItemSpec.getItem().getJSONPretty("keyID"));
        System.out.println(putItemSpec.getItem().getJSONPretty("longURL"));
        try {
            table.putItem(putItemSpec);
            return true;
        }catch(ConditionalCheckFailedException c){
            System.out.println("unmarshalling exception : "+ c.getMessage());
            return false;
        } catch (Exception e) {
            System.out.println("EXCEPTION : " + e.getMessage().toString());
            return false;
        }
    }
}
