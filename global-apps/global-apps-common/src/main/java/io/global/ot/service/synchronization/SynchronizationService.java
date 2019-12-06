package io.global.ot.service.synchronization;

import io.datakernel.async.service.EventloopService;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.ot.OTSystem;
import io.datakernel.promise.Promise;
import io.datakernel.promise.Promises;
import io.global.common.PubKey;
import io.global.ot.api.RepoID;
import io.global.ot.client.MyRepositoryId;
import io.global.ot.client.OTDriver;
import io.global.ot.client.RepoSynchronizer;
import io.global.ot.service.CommonUserContainer;
import io.global.ot.service.messaging.MessagingService;
import io.global.ot.shared.SharedRepo;
import io.global.ot.shared.SharedReposOTState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static io.datakernel.common.collection.CollectionUtils.first;
import static io.datakernel.promise.Promises.retry;
import static io.global.ot.client.RepoSynchronizer.DEFAULT_INITIAL_BACKOFF;

public final class SynchronizationService<D> implements EventloopService {
	private final Eventloop eventloop;
	private final CommonUserContainer<D> commonUserContainer;
	private final OTDriver driver;
	private final OTSystem<D> system;
	private final Map<String, RepoSynchronizer<D>> synchronizers = new HashMap<>();
	@Nullable
	private Duration initialBackoff;

	private SynchronizationService(Eventloop eventloop, CommonUserContainer<D> commonUserContainer, OTDriver driver, OTSystem<D> system) {
		this.eventloop = eventloop;
		this.commonUserContainer = commonUserContainer;
		this.driver = driver;
		this.system = system;
	}

	public static <D> SynchronizationService<D> create(Eventloop eventloop, OTDriver driver, CommonUserContainer<D> commonUserContainer, OTSystem<D> system) {
		return new SynchronizationService<>(eventloop, commonUserContainer, driver, system);
	}

	public SynchronizationService<D> withInitialBackOff(Duration initialBackOff){
		this.initialBackoff = initialBackOff;
		return this;
	}

	@NotNull
	@Override
	public Eventloop getEventloop() {
		return eventloop;
	}

	@NotNull
	@Override
	public Promise<Void> start() {
		SharedReposOTState resourceListState = commonUserContainer.getResourceListState();
		Set<SharedRepo> sharedRepos = resourceListState.getSharedRepos();
		sharedRepos.forEach(resource -> {
			ensureSynchronizer(resource.getId()).sync(resource.getParticipants());
			sendMessageToParticipants(resource);
		});

		resourceListState.setListener(sharedReposOperation -> {
			SharedRepo sharedRepo = sharedReposOperation.getSharedRepo();
			if (sharedReposOperation.isRemove()) {
				synchronizers.remove(sharedRepo.getId()).stop();
			} else {
				ensureSynchronizer(sharedRepo.getId()).sync(sharedRepo.getParticipants());
				sendMessageToParticipants(sharedRepo);
			}
		});

		return Promise.complete();
	}

	@NotNull
	@Override
	public Promise<Void> stop() {
		return Promises.all(synchronizers.values().stream().map(RepoSynchronizer::stop));
	}

	public RepoSynchronizer<D> ensureSynchronizer(String id) {
		return synchronizers.computeIfAbsent(id, $ ->
				RepoSynchronizer.create(eventloop, driver, system, getMyRepositoryId(id))
						.withInitialBackOff(initialBackoff == null ? DEFAULT_INITIAL_BACKOFF : initialBackoff));
	}

	private MyRepositoryId<D> getMyRepositoryId(String id) {
		MyRepositoryId<D> myRepositoryId = commonUserContainer.getMyRepositoryId();
		String name = myRepositoryId.getRepositoryId().getName() + '/' + id;
		RepoID repoId = RepoID.of(myRepositoryId.getPrivKey(), name);
		return new MyRepositoryId<>(repoId, myRepositoryId.getPrivKey(), myRepositoryId.getDiffCodec());
	}

	public void sendMessageToParticipants(SharedRepo sharedRepo) {
		RepoID repoId = commonUserContainer.getMyRepositoryId().getRepositoryId();
		MessagingService messagingService = commonUserContainer.getMessagingService();
		Set<PubKey> participants = sharedRepo.getParticipants();
		String id = sharedRepo.getId();
		Promises.all(participants.stream()
				.filter(participant -> !sharedRepo.isMessageSent(participant) &&
						!participant.equals(repoId.getOwner()))
				.map(participant ->
						driver.getHeads(RepoID.of(participant, repoId + "/" + id))
								.whenComplete((heads, e) -> {
									if (e == null && !heads.isEmpty() && !first(heads).isRoot()) {
										sharedRepo.setMessageSent(participant);
									} else {
										retry(() -> messagingService
												.sendCreateMessage(participant, id, sharedRepo.getName(), participants))
												.get()
												.whenResult($ -> sharedRepo.setMessageSent(participant));
									}
								})));
	}
}
