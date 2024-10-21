package com.task10;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.*;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.syndicate.deployment.model.environment.ValueTransformer.USER_POOL_NAME_TO_CLIENT_ID;
import static com.syndicate.deployment.model.environment.ValueTransformer.USER_POOL_NAME_TO_USER_POOL_ID;

@LambdaHandler(
    lambdaName = "api_handler",
	roleName = "api_handler-role",
	isPublishVersion = false,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@DependsOn(resourceType = ResourceType.COGNITO_USER_POOL, name = "${booking_userpool}")
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "REGION", value = "${region}"),
		@EnvironmentVariable(key = "COGNITO_ID", value = "${booking_userpool}", valueTransformer = USER_POOL_NAME_TO_USER_POOL_ID),
		@EnvironmentVariable(key = "CLIENT_ID", value = "${booking_userpool}", valueTransformer = USER_POOL_NAME_TO_CLIENT_ID),
		@EnvironmentVariable(key = "tables_table", value = "Tables"),
		@EnvironmentVariable(key = "reservations_table", value = "Reservations"),
		@EnvironmentVariable(key = "booking_userpool", value = "simple-booking-userpool")
	}
)
@LambdaUrlConfig(
		authType = AuthType.NONE,
		invokeMode = InvokeMode.BUFFERED
)
public class ApiHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

	private final AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
	private final DynamoDB dynamoDB = new DynamoDB(client);
	private final Table tablesTable = dynamoDB.getTable(System.getenv("tables_table"));
	private final Table reservationsTable = dynamoDB.getTable(System.getenv("reservations_table"));
	private final CognitoIdentityProviderClient cognitoClient = CognitoIdentityProviderClient.builder().build();
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent requestEvent, Context context) {
		String path = requestEvent.getRequestContext().getHttp().getPath();
		String method = requestEvent.getRequestContext().getHttp().getMethod();

		try {
			switch (path) {
				case "/signup":
					if ("POST".equals(method)) {
						return handleSignup(requestEvent);
					}
					break;
				case "/signin":
					if ("POST".equals(method)) {
						return handleSignin(requestEvent);
					}
					break;
				case "/tables":
					if ("GET".equals(method)) {
						return handleGetTables(requestEvent);
					} else if ("POST".equals(method)) {
						return handlePostTable(requestEvent);
					}
					break;
				case "/tables/{tableId}":
					if ("GET".equals(method)) {
						return handleGetTableById(requestEvent);
					}
					break;
				case "/reservations":
					if ("POST".equals(method)) {
						return handlePostReservation(requestEvent);
					} else if ("GET".equals(method)) {
						return handleGetReservations(requestEvent);
					}
					break;
				default:
					return buildResponse(404, "Not Found");
			}
		} catch (Exception e) {
			context.getLogger().log("Error: " + e.getMessage());
            try {
                return buildResponse(400, "Bad Request: " + e.getMessage());
            } catch (JsonProcessingException ex) {
                throw new RuntimeException(ex);
            }
        }

        try {
            return buildResponse(400, "Bad Request");
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

	private APIGatewayV2HTTPResponse handleSignup(APIGatewayV2HTTPEvent requestEvent) throws Exception {
		/*Map<String, String> requestBody = objectMapper.readValue(requestEvent.getBody(), Map.class);
		String email = requestBody.get("email");
		String password = requestBody.get("password");

		AdminCreateUserRequest createUserRequest = AdminCreateUserRequest.builder()
				.userPoolId(System.getenv("COGNITO_ID"))
				.username(email)
				.temporaryPassword(password)
				.messageAction("SUPPRESS")
				.build();
		cognitoClient.adminCreateUser(createUserRequest);

		return buildResponse(200, "Sign-up process is successful");*/
		Map<String, String> requestBody = objectMapper.readValue(requestEvent.getBody(), Map.class);
		String firstName = requestBody.get("firstName");
		String lastName = requestBody.get("lastName");
		String email = requestBody.get("email");
		String password = requestBody.get("password");

		// Create the user in Cognito
		AdminCreateUserRequest createUserRequest = AdminCreateUserRequest.builder()
				.userPoolId(System.getenv("COGNITO_ID"))
				.username(email)
				.temporaryPassword(password)
				.userAttributes(
						AttributeType.builder().name("email").value(email).build(),
						AttributeType.builder().name("email_verified").value("true").build(),
						AttributeType.builder().name("given_name").value(firstName).build(),
						AttributeType.builder().name("family_name").value(lastName).build()
				)
				.messageAction("SUPPRESS")
				.build();

		cognitoClient.adminCreateUser(createUserRequest);

		// Set the user's password to the provided password
		AdminSetUserPasswordRequest setPasswordRequest = AdminSetUserPasswordRequest.builder()
				.userPoolId(System.getenv("COGNITO_ID"))
				.username(email)
				.password(password)
				.permanent(true)
				.build();

		cognitoClient.adminSetUserPassword(setPasswordRequest);

		return buildResponse(200, "Sign-up process is successful");
	}

	private APIGatewayV2HTTPResponse handleSignin(APIGatewayV2HTTPEvent requestEvent) throws Exception {
		Map<String, String> requestBody = objectMapper.readValue(requestEvent.getBody(), Map.class);
		String email = requestBody.get("email");
		String password = requestBody.get("password");

		AdminInitiateAuthRequest authRequest = AdminInitiateAuthRequest.builder()
				.userPoolId(System.getenv("COGNITO_ID"))
				.clientId(System.getenv("CLIENT_ID"))
				.authFlow(AuthFlowType.ADMIN_NO_SRP_AUTH)
				.authParameters(Map.of("USERNAME", email, "PASSWORD", password))
				.build();

		AdminInitiateAuthResponse authResult = cognitoClient.adminInitiateAuth(authRequest);
		String idToken = authResult.authenticationResult().idToken();

		return buildResponse(200, Map.of("accessToken", idToken));
	}

	private APIGatewayV2HTTPResponse handleGetTables(APIGatewayV2HTTPEvent requestEvent) throws JsonProcessingException {
		try {
			List<Map<String, Object>> tables = new ArrayList<>();
			ItemCollection<ScanOutcome> items = tablesTable.scan();

			for (Item item : items) {
				tables.add(item.asMap());
			}

			return buildResponse(200, tables);
		} catch (Exception e) {
			return buildResponse(400, "Bad Request: " + e.getMessage());
		}
	}

	private APIGatewayV2HTTPResponse handlePostTable(APIGatewayV2HTTPEvent requestEvent) throws JsonProcessingException {
		try {
			Map<String, Object> requestBody = objectMapper.readValue(requestEvent.getBody(), Map.class);
			
			Item item = new Item()
					.withPrimaryKey("id", requestBody.get("id"))
					.withInt("number", (Integer) requestBody.get("number"))
					.withInt("places", (Integer) requestBody.get("places"))
					.withBoolean("isVip", (Boolean) requestBody.get("isVip"));

			if (requestBody.containsKey("minOrder")) {
				item.withInt("minOrder", (Integer) requestBody.get("minOrder"));
			}

			tablesTable.putItem(item);

			return buildResponse(200, Map.of("id", requestBody.get("id")));
		} catch (Exception e) {
			return buildResponse(400, "Bad Request: " + e.getMessage());
		}
	}

	private APIGatewayV2HTTPResponse handleGetTableById(APIGatewayV2HTTPEvent requestEvent) throws JsonProcessingException {
		try {
			String tableId = requestEvent.getPathParameters().get("id");
			Item item = tablesTable.getItem("id", Integer.parseInt(tableId));

			if (item == null) {
				return buildResponse(400, "Bad Request");
			}

			return buildResponse(200, item.asMap());
		} catch (Exception e) {
			return buildResponse(400, "Bad Request: " + e.getMessage());
		}
	}

	private APIGatewayV2HTTPResponse handlePostReservation(APIGatewayV2HTTPEvent requestEvent) throws JsonProcessingException {
		try {
			Map<String, Object> requestBody = objectMapper.readValue(requestEvent.getBody(), Map.class);
			String reservationId = java.util.UUID.randomUUID().toString();

			Item item = new Item()
					.withPrimaryKey("id", reservationId)
					.withInt("tableNumber", (Integer) requestBody.get("tableNumber"))
					.withString("clientName", (String) requestBody.get("clientName"))
					.withString("phoneNumber", (String) requestBody.get("phoneNumber"))
					.withString("date", (String) requestBody.get("date"))
					.withString("slotTimeStart", (String) requestBody.get("slotTimeStart"))
					.withString("slotTimeEnd", (String) requestBody.get("slotTimeEnd"));

			reservationsTable.putItem(item);

			return buildResponse(200, Map.of("reservationId", reservationId));
		} catch (Exception e) {
			return buildResponse(400, "Bad Request: " + e.getMessage());
		}
	}

	private APIGatewayV2HTTPResponse handleGetReservations(APIGatewayV2HTTPEvent requestEvent) throws JsonProcessingException {
		try {
			List<Map<String, Object>> reservations = new ArrayList<>();
			ItemCollection<ScanOutcome> items = reservationsTable.scan();

			for (Item item : items) {
				Map<String, Object> reservation = Map.of(
						"tableNumber", item.getInt("tableNumber"),
						"clientName", item.getString("clientName"),
						"phoneNumber", item.getString("phoneNumber"),
						"date", item.getString("date"),
						"slotTimeStart", item.getString("slotTimeStart"),
						"slotTimeEnd", item.getString("slotTimeEnd")
				);
				reservations.add(reservation);
			}

			return buildResponse(200, Map.of("reservations", reservations));
		} catch (Exception e) {
			return buildResponse(400, "Bad Request: " + e.getMessage());
		}
	}

	private APIGatewayV2HTTPResponse buildResponse(int statusCode, Object body) throws JsonProcessingException {
		return APIGatewayV2HTTPResponse.builder()
				.withStatusCode(statusCode)
				.withHeaders(Map.of("Content-Type", "application/json"))
				.withBody(objectMapper.writeValueAsString(body))
				.build();
	}
}
