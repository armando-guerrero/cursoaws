package com.task05;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
public class ApiHandler implements RequestHandler<Object, Map<String, Object>> {

	private static final int SC_CREATED = 201;
	private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
	private final AmazonDynamoDB client = AmazonDynamoDBClientBuilder.defaultClient();
	private final DynamoDB dynamoDB = new DynamoDB(client);
	private final Table table = dynamoDB.getTable(System.getenv("target_table"));

	@Override
	public Map<String, Object> handleRequest(Object request, Context context) {
		System.out.println("Entra bien...");
		try {
			Map<String, Object> requestBody = objectMapper.convertValue(request, new TypeReference<Map<String, Object>>() {});
			requestBody.forEach((key, value) -> System.out.println("[Key] : " + key + " [Value] : " + value));
			String id = UUID.randomUUID().toString();
			//Instant createdAt = Instant.now();
			LocalDate createdAt = LocalDate.now();
			String isoDateTime = DateTimeFormatter.ISO_INSTANT.format(createdAt);

			Item item = new Item()
					.withPrimaryKey("id", id)
					.withInt("principalId", (Integer) requestBody.get("principalId"))
					.withString("createdAt", isoDateTime)
					.withMap("body", (Map<String, String>) requestBody.get("content"));

			table.putItem(item);

			Map<String, Object> responseBody = new HashMap<>();
			responseBody.put("statusCode", SC_CREATED);
			responseBody.put("event", item.asMap());

			return responseBody;
		} catch (Exception e) {
			context.getLogger().log("Error: " + e.getMessage());
			Map<String, Object> errorResponse = new HashMap<>();
			errorResponse.put("statusCode", 500);
			errorResponse.put("message", "Internal server error");
			return errorResponse;
		}
	}
}