
package com.task10.handler;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.PutItemOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GetReservationsHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent requestEvent, Context context) {

        System.out.println("going to get reservations data from dynamodb table");
        String reservationsTableName = System.getenv("reservations_table");
        String region = System.getenv("REGION");
        AmazonDynamoDBAsync dynamoDBClient = AmazonDynamoDBAsyncClientBuilder.standard().withRegion(region).build();

        ScanRequest scanRequest = new ScanRequest()
                .withTableName(reservationsTableName);
        ScanResult result = dynamoDBClient.scan(scanRequest);

        ArrayList<Object> tempList = new ArrayList<Object>();
        for (Map<String, AttributeValue> item : result.getItems()) {
            Map<String, Object> simpleMap = new HashMap<String, Object>();
            simpleMap.put("tableNumber", Integer.parseInt(item.get("tableNumber").getN()));
            simpleMap.put("clientName", item.get("clientName").getS());
            simpleMap.put("phoneNumber", item.get("phoneNumber").getS());
            simpleMap.put("date", item.get("date").getS());
            simpleMap.put("slotTimeStart", item.get("slotTimeStart").getS());
            simpleMap.put("slotTimeEnd", item.get("slotTimeEnd").getS());
            tempList.add(new JSONObject(simpleMap));
        }

        return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withBody(new JSONObject().put("reservations", tempList).toString());
    }

}
