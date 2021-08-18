package eu.solven.cleanthat.git_abstraction;

import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.stream.Stream;

import org.kohsuke.github.GHCommitPointer;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.solven.cleanthat.code_provider.github.event.pojo.GitRepoBranchSha1;
import eu.solven.cleanthat.github.CleanthatRefFilterProperties;

/**
 * Enable a Facade over RewiewRequestProvider
 * 
 * @author Benoit Lacelle
 *
 */
public class GithubFacade {
	private static final Logger LOGGER = LoggerFactory.getLogger(GithubFacade.class);

	final GitHub github;
	final String repoName;

	public GithubFacade(GitHub github, String repoName) {
		this.github = github;
		this.repoName = repoName;
	}

	public String toFullGitRef(GHCommitPointer ghCommitPointer) {
		String githubRef = ghCommitPointer.getRef();
		return toFullGitRef(githubRef);
	}

	public String toFullGitRef(String githubRef) {
		return CleanthatRefFilterProperties.BRANCHES_PREFIX + githubRef;
	}

	public Stream<GHPullRequest> findAnyPrHeadMatchingRef(String ref) throws IOException {
		return github.getRepository(repoName).getPullRequests(GHIssueState.OPEN).stream().filter(pr -> {
			return ref.equals(toFullGitRef(pr.getHead()));
		});
	}

	public Optional<GHPullRequest> findFirstPrBaseMatchingRef(String ref) throws IOException {
		return github.getRepository(repoName).getPullRequests(GHIssueState.OPEN).stream().filter(pr -> {
			return ref.equals(toFullGitRef(pr.getBase()));
		}).findAny();
	}

	public Optional<?> openPrIfNoneExists(GitRepoBranchSha1 base, GitRepoBranchSha1 head, String title, String body)
			throws IOException {
		if (!base.getRepoName().equals(repoName)) {
			throw new IllegalArgumentException(
					"The base (" + base + ") is not a ref of current repo (" + repoName + ")");
		} else if (!head.getRepoName().equals(repoName)) {
			// TODO We would need the head username to build a headRef like 'username:branch'
			// This way rely on the match Github allows a single fork per username/organisation
			throw new UnsupportedOperationException(
					"The head (" + head + ") has a different repository than current repo (" + repoName + ")");
		}

		String baseFullRef = base.getRef();
		if (!baseFullRef.startsWith(CleanthatRefFilterProperties.BRANCHES_PREFIX)) {
			throw new IllegalArgumentException("The base has to be a branch. ref=" + baseFullRef);
		}
		String headRef = head.getRef();

		GHRepository repository = github.getRepository(repoName);
		Optional<GHPullRequest> existingPr = repository.getPullRequests(GHIssueState.ALL).stream().filter(pr -> {
			return baseFullRef.equals(toFullGitRef(pr.getBase())) && headRef.equals(toFullGitRef(pr.getHead()));
		}).findAny();

		if (existingPr.isEmpty()) {
			String baseBranchName = baseFullRef.substring(CleanthatRefFilterProperties.BRANCHES_PREFIX.length());

			// We create only Draft PR for now
			// Maintainers are of-course allowed to modify CleanThat PR
			GHPullRequest pr = repository.createPullRequest(title, baseBranchName, headRef, body, true, true);

			return Optional.of(pr.getNumber());
		} else {
			LOGGER.info("Existing PR: {}", existingPr.get());
			return Optional.empty();
		}
	}

	public void removeRef(GitRepoBranchSha1 ref) throws IOException {
		GHRef remoteRef = getRef(ref.getRepoName(), ref.getRef());
		URL remoteRefUrl = remoteRef.getUrl();
		LOGGER.info("About to delete {}", remoteRefUrl);
		remoteRef.delete();
		LOGGER.info("Deleted {}", remoteRefUrl);
	}

	public GHRef getRef(String repoName, String refName) throws IOException {
		GHRepository repo = github.getRepository(repoName);

		if (!refName.startsWith(CleanthatRefFilterProperties.REFS_PREFIX)) {
			throw new IllegalArgumentException("Invalid ref: " + refName);
		}

		// repository.getRef expects a ref name without the leading 'refs/'
		String githubRefName = refName.substring(CleanthatRefFilterProperties.REFS_PREFIX.length());

		return repo.getRef(githubRefName);
	}

}
