
package com.task10.handler;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class GetTablesHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent requestEvent, Context context) {

        String tablesTableName = System.getenv("tables_table");
        String region = System.getenv("REGION");
        AmazonDynamoDBAsync dynamoDBClient = AmazonDynamoDBAsyncClientBuilder.standard().withRegion(region).build();

        ScanRequest scanRequest = new ScanRequest()
                .withTableName(tablesTableName);

        ScanResult result = dynamoDBClient.scan(scanRequest);

        ArrayList<Object> tempList = new ArrayList<Object>();
        for (Map<String, AttributeValue> item : result.getItems()) {
            Map<String, Object> simpleMap = new HashMap<String, Object>();
            simpleMap.put("id", Integer.valueOf(item.get("id").getN()));
            simpleMap.put("number", Integer.valueOf(item.get("number").getN()));
            simpleMap.put("places", Integer.valueOf(item.get("places").getN()));
            simpleMap.put("isVip", item.get("isVip").getBOOL());
            simpleMap.put("minOrder", Integer.valueOf(item.get("minOrder").getN()));
            tempList.add(new JSONObject(simpleMap));
        }

        return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withBody(new JSONObject().put("tables", tempList).toString());
    }

}
