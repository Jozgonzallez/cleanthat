package eu.solven.cleanthat.aws.dynamodb;

import java.io.IOException;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.document.internal.InternalUtils;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.nimbusds.jose.JOSEException;

import eu.solven.cleanthat.code_provider.github.event.pojo.GithubWebhookEvent;
import eu.solven.cleanthat.lambda.AWebhooksLambdaFunction;
import eu.solven.cleanthat.lambda.dynamodb.SaveToDynamoDb;
import eu.solven.cleanthat.lambda.step1_checkconfiguration.CheckConfigWebhooksLambdaFunction;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = { CheckConfigWebhooksLambdaFunction.class })
public class ITProcessLocallyDynamoDbEvent_CheckConfig {
	@Autowired
	AWebhooksLambdaFunction lambdaFunction;

	@Test
	public void testInitWithDefaultConfiguration() throws IOException, JOSEException {
		AmazonDynamoDB dynamoDbClient = SaveToDynamoDb.makeDynamoDbClient();

		String key = "random-d1f94a67-ffe5-4334-9e78-b47e21486581";
		GetItemResult item = dynamoDbClient.getItem(new GetItemRequest().withTableName("cleanthat_webhooks_github")
				.withKey(Map.of(GithubWebhookEvent.X_GIT_HUB_DELIVERY, new AttributeValue().withS(key))));

		Map<String, AttributeValue> dynamoDbItem = item.getItem();
		if (dynamoDbItem == null) {
			throw new IllegalArgumentException("There is no item with key=" + key);
		}

		@SuppressWarnings("deprecation")
		Map<String, ?> dynamoDbPureJson = InternalUtils.toSimpleMapValue(dynamoDbItem);

		Map<String, ?> output = lambdaFunction.ingressRawWebhook().apply(dynamoDbPureJson);

		Assertions.assertThat(output).hasSize(1);
	}
}
