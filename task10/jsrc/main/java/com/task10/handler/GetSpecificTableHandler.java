
package com.task10.handler;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.json.JSONObject;

import java.util.Map;

public class GetSpecificTableHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent requestEvent, Context context) {

        String tablesTableName = System.getenv("tables_table");
        String region = System.getenv("REGION");
        DynamoDB dynamoDB = new DynamoDB(AmazonDynamoDBAsyncClientBuilder.standard().withRegion(region).build());
        Table table = dynamoDB.getTable(tablesTableName);

        Map<String, String> pathParams = requestEvent.getPathParameters();
        System.out.println("table id from path param : "+pathParams.get("tableId"));

        Item item = table.getItem("id", Integer.parseInt(pathParams.get("tableId")));

        return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withBody(new JSONObject(item.toJSONPretty()).toString());
    }

}
