
package com.task10.handler;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PostReservationsHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent requestEvent, Context context) {

        String reservationsTableName = System.getenv("reservations_table");
        String region = System.getenv("REGION");

        JSONObject reservationsData = new JSONObject(requestEvent.getBody());

        String id = UUID.randomUUID().toString();

        String tablesTableName = System.getenv("tables_table");
        AmazonDynamoDBAsync dynamoDBClient = AmazonDynamoDBAsyncClientBuilder.standard().withRegion(region).build();
        ScanRequest tablesScanRequest = new ScanRequest().withTableName(tablesTableName);
        ScanResult tablesResult = dynamoDBClient.scan(tablesScanRequest);
        boolean tableExists = false;
        System.out.println("reservation table number : "+reservationsData.get("tableNumber").toString());
        for (Map<String, AttributeValue> item : tablesResult.getItems()) {
            System.out.println("existing table id : "+item.get("number").getN());
            if(item.get("number").getN().equalsIgnoreCase(reservationsData.get("tableNumber").toString())){
                tableExists = true;
            }
        }

        boolean isOverlaps = false;
        ScanRequest reservationsScanRequest = new ScanRequest().withTableName(reservationsTableName);
        ScanResult reservationsResult = dynamoDBClient.scan(reservationsScanRequest);
        List<Map<String, AttributeValue>> existingReservations = new ArrayList<Map<String, AttributeValue>>();
        for (Map<String, AttributeValue> item : reservationsResult.getItems()) {
            if(item.get("tableNumber").getN().equalsIgnoreCase(reservationsData.get("tableNumber").toString())
            && item.get("date").getN().equalsIgnoreCase(reservationsData.get("date").toString())){
                existingReservations.add(item);
            }
        }

        for(Map<String, AttributeValue> existingReservation: existingReservations){
            int slot_time_start = Integer.parseInt(reservationsData.get("slotTimeStart").toString().substring(0,2));
            int slot_time_end = Integer.parseInt(reservationsData.get("slotTimeEnd").toString().substring(0,2));
            int existing_time_start = Integer.parseInt(existingReservation.get("slotTimeStart").getS().substring(0,2));
            int existing_time_end = Integer.parseInt(existingReservation.get("slotTimeEnd").getS().substring(0,2));
            if (slot_time_start < existing_time_end && slot_time_end > existing_time_start){
                isOverlaps = true;
            }
        }

        if(tableExists && !isOverlaps) {
            DynamoDB dynamoDB = new DynamoDB(dynamoDBClient);
            Table table = dynamoDB.getTable(reservationsTableName);

            Item item = new Item().withPrimaryKey("id", id)
                    .withNumber("tableNumber", Integer.parseInt(reservationsData.get("tableNumber").toString()))
                    .withString("clientName", reservationsData.get("clientName").toString())
                    .withString("phoneNumber", reservationsData.get("phoneNumber").toString())
                    .withString("date", reservationsData.get("date").toString())
                    .withString("slotTimeStart", reservationsData.get("slotTimeStart").toString())
                    .withString("slotTimeEnd", reservationsData.get("slotTimeEnd").toString());

            table.putItem(item);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(new JSONObject().put("reservationId", id).toString());
        }
        else {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody(new JSONObject().put("message", "given table number doesn't exists").toString());
        }
    }

}
