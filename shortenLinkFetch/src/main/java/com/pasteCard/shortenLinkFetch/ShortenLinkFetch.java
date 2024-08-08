package com.pasteCard.shortenLinkFetch;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;

import java.util.HashMap;
import java.util.Map;

public class ShortenLinkFetch implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    // private static final String DOMAIN = "https://yg8x4tc026.execute-api.ap-south-1.amazonaws.com/prod";
    private static final String TABLE_NAME = "pasteCard-short";
    private final Map<String, String> headers = new HashMap<>();


    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Headers", "*");
        try {
            String key = extractKeyFromPath(input.getPath());
            System.out.println("KEY : " + key);
            String longURL = getLongURLFromDynamoDB(key);

            System.out.println(longURL);

            if (longURL != null) {
                headers.put("Location", longURL);
                return new APIGatewayProxyResponseEvent().withHeaders(headers).withStatusCode(302);
            } else {
                return new APIGatewayProxyResponseEvent().withHeaders(headers).withStatusCode(404).withBody("Long URL not found for the given key!");
            }
        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(500).withBody("Error: " + e.getMessage());
        }
    }

    private String extractKeyFromPath(String path) {
        String[] parts = path.split("/");
        if (parts.length >= 2) {
            return parts[2];
        }
        return "";
    }

    private String getLongURLFromDynamoDB(String key) {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.defaultClient();
        DynamoDB dynamoDB = new DynamoDB(client);
        Table table = dynamoDB.getTable(TABLE_NAME);

        Item item = table.getItem("keyID", key);

        if (item != null) {
            return item.getString("longURL");
        } else {
            return null;
        }
    }
}
