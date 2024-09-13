package com.task05;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.Architecture;
import com.syndicate.deployment.model.DeploymentRuntime;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@LambdaHandler(
    lambdaName = "api_handler",
	roleName = "api_handler-role",
	isPublishVersion = false,
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
	runtime = DeploymentRuntime.JAVA17,
	architecture = Architecture.ARM64
)
@LambdaUrlConfig(
		authType = AuthType.NONE,
		invokeMode = InvokeMode.BUFFERED
)
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "target_table", value = "Events")}
)
public class ApiHandler  implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

	private static final int SC_CREATED = 201;
	private static final int SC_NOT_FOUND = 404;
	private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
	private final AmazonDynamoDB client = AmazonDynamoDBClientBuilder.defaultClient();
	private final DynamoDB dynamoDB = new DynamoDB(client);
	private final Table table = dynamoDB.getTable(System.getenv("target_table"));
	private final Map<ApiHandler.RouteKey, Function<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse>> routeHandlers = Map.of(
			new ApiHandler.RouteKey("POST", "/events"), this::handleGetEvents
	);

	@Override
	public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent requestEvent, Context context) {
		ApiHandler.RouteKey routeKey = new ApiHandler.RouteKey(getMethod(requestEvent), getPath(requestEvent));
		return routeHandlers.getOrDefault(routeKey, this::notFoundResponse).apply(requestEvent);
	}

	private APIGatewayV2HTTPResponse handleGetEvents(APIGatewayV2HTTPEvent requestEvent) {
		try {
			Map<String, Object> requestBody = objectMapper.readValue(requestEvent.getBody(), Map.class);
			System.out.println("Request body: ........");
			System.out.println("Request body: " + requestBody.get("content").toString());
			String id = UUID.randomUUID().toString();
			String createdAt = Instant.now().toString();
			System.out.println("Request body: " + requestBody.get("content").toString());
			Item item = new Item()
					.withPrimaryKey("id", id.toString())
					.withString("principalId", requestBody.get("principalId").toString())
					.withString("createdAt", createdAt)
					.withString("body", requestBody.get("content").toString());
			System.out.println("Ready to write un table ....");

			table.putItem(item);

			Map<String, Object> responseBody = new HashMap<>();
			responseBody.put("statusCode", SC_CREATED);
			responseBody.put("event", item.asMap());

			return APIGatewayV2HTTPResponse.builder()
					.withStatusCode(SC_CREATED)
					.withBody(objectMapper.writeValueAsString(responseBody))
					.build();
		} catch (Exception e) {
			//context.getLogger().log("Error: " + e.getMessage());
			return APIGatewayV2HTTPResponse.builder()
					.withStatusCode(500)
					.withBody("{\"message\": \"Internal server error\"}")
					.build();
		}
	}

	private APIGatewayV2HTTPResponse notFoundResponse(APIGatewayV2HTTPEvent requestEvent) {
		return buildResponse(SC_NOT_FOUND, ApiHandler.Body.statusCode("Bad request syntax or unsupported method. Request path: /cmtr-12e2dc5a. HTTP method: POST".formatted(
				getMethod(requestEvent),
				getPath(requestEvent)
		)));
	}

	private String getMethod(APIGatewayV2HTTPEvent requestEvent) {
		return requestEvent.getRequestContext().getHttp().getMethod();
	}

	private String getPath(APIGatewayV2HTTPEvent requestEvent) {
		return requestEvent.getRequestContext().getHttp().getPath();
	}

	private record RouteKey(String method, String path) {
	}

	private APIGatewayV2HTTPResponse buildResponse(int statusCode, Object body) {
		try {
			return APIGatewayV2HTTPResponse.builder()
					.withStatusCode(statusCode)
					.withBody(objectMapper.writeValueAsString(body))
					.build();
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	private record Body(String message, int statusCode) {
		static Object statusCode(String statusCode) {
			return new ApiHandler.Body("Bad request syntax or unsupported method. Request path: /cmtr-12e2dc5a. HTTP method: GET", 400);
		}

		static Object ok(String message) {
			return new ApiHandler.Body(message,200);
		}


	}
}