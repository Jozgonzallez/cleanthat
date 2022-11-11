package eu.solven.cleanthat.lambda.step0_checkwebhook;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.github.seratch.jslack.Slack;

import eu.solven.cleanthat.code_provider.github.event.IGitWebhookHandler;
import eu.solven.cleanthat.code_provider.github.event.IGitWebhookHandlerFactory;
import eu.solven.cleanthat.code_provider.github.event.pojo.CleanThatWebhookEvent;
import eu.solven.cleanthat.code_provider.github.event.pojo.GithubWebhookEvent;
import eu.solven.cleanthat.codeprovider.git.GitWebhookRelevancyResult;
import eu.solven.cleanthat.lambda.AWebhooksLambdaFunction;
import eu.solven.cleanthat.lambda.dynamodb.SaveToDynamoDb;
import eu.solven.pepper.collection.PepperMapHelper;

/**
 * Used to filter relevant webhooks for useless webhooks.
 * 
 * This first step should not depends at all on the CodeProvider API (i.e. it works without having to authenticate
 * ourselves at all). We just analyse the webhook content to filter out what's irrelevant.
 * 
 * @author Benoit Lacelle
 *
 */
// https://docs.github.com/en/developers/github-marketplace/using-the-github-marketplace-api-in-your-app/webhook-events-for-the-github-marketplace-api
public class CheckWebhooksLambdaFunction extends AWebhooksLambdaFunction {
	private static final Logger LOGGER = LoggerFactory.getLogger(CheckWebhooksLambdaFunction.class);

	public static void main(String[] args) {
		SpringApplication.run(CheckWebhooksLambdaFunction.class, args);
	}

	@SuppressWarnings("PMD.CloseResource")
	@Override
	protected Map<String, ?> unsafeProcessOneEvent(IWebhookEvent input) {
		GithubWebhookEvent githubEvent = (GithubWebhookEvent) input;

		Optional<Map<String, ?>> optMarketplacePurchase =
				PepperMapHelper.getOptionalAs(githubEvent.getBody(), "marketplace_purchase");
		if (optMarketplacePurchase.isPresent()) {
			Slack slack = getSlack();

			MarketPlaceEventManager
					.handleMarketplaceEvent(getAppContext().getEnvironment(), slack, githubEvent.getBody());
			return Map.of("event_type", "marketplace_purchase");
		}

		IGitWebhookHandlerFactory githubFactory = getAppContext().getBean(IGitWebhookHandlerFactory.class);

		// TODO Cache the Github instance for the JWT duration
		IGitWebhookHandler makeWithFreshJwt;
		try {
			makeWithFreshJwt = githubFactory.makeWithFreshAuth();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		GitWebhookRelevancyResult processAnswer = makeWithFreshJwt.filterWebhookEventRelevant(githubEvent);

		if (processAnswer.isReviewRequestOpen() || processAnswer.isPushBranch()) {
			AmazonDynamoDB client = SaveToDynamoDb.makeDynamoDbClient();

			Map<String, Object> acceptedEvent = new LinkedHashMap<>();

			// We may add details from processAnswer
			acceptedEvent.put("github", Map.of("body", githubEvent.getBody(), "headers", githubEvent.getHeaders()));

			SaveToDynamoDb.saveToDynamoDb("cleanthat_webhooks_github",
					new CleanThatWebhookEvent(Map.of(), acceptedEvent),
					client);
			return Map.of("whatever", "accepted");
		} else {
			LOGGER.info("Neither a PR-open event, nor a push-branch event");
			return Map.of("whatever", "discarded");
		}

	}

	@SuppressWarnings("PMD.CloseResource")
	private Slack getSlack() {
		return getAppContext().getBean(Slack.class);
	}
}
