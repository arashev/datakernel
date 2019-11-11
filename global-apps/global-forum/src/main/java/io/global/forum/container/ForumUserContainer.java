package io.global.forum.container;

import io.datakernel.async.Promise;
import io.datakernel.async.Promises;
import io.datakernel.di.annotation.Inject;
import io.datakernel.di.core.InstanceProvider;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.ot.OTStateManager;
import io.global.comm.container.CommState;
import io.global.common.KeyPair;
import io.global.forum.dao.ForumDao;
import io.global.forum.ot.ForumMetadata;
import io.global.ot.api.CommitId;
import io.global.ot.service.UserContainer;
import io.global.ot.value.ChangeValue;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.datakernel.util.LogUtils.toLogger;

public final class ForumUserContainer implements UserContainer {
	private static final Logger logger = LoggerFactory.getLogger(ForumUserContainer.class);

	@Inject
	private Eventloop eventloop;

	@Inject
	private InstanceProvider<ForumDao> forumDao;
	@Inject
	private OTStateManager<CommitId, ChangeValue<ForumMetadata>> metadataStateManager;
	@Inject
	private CommState comm;
	@Inject
	private KeyPair keys;

	private ForumUserContainer() {
	}

	@Inject
	public static ForumUserContainer create() {
		return new ForumUserContainer();
	}

	@Override
	@NotNull
	public Eventloop getEventloop() {
		return eventloop;
	}

	@NotNull
	@Override
	public Promise<?> start() {
		return comm.start()
				.then($ -> metadataStateManager.start())
				.whenComplete(toLogger(logger, "start"));
	}

	@NotNull
	@Override
	public Promise<?> stop() {
		return comm.stop()
				.then($ -> metadataStateManager.stop())
				.whenComplete(toLogger(logger, "stop"));
	}

	public OTStateManager<CommitId, ChangeValue<ForumMetadata>> getMetadataStateManager() {
		return metadataStateManager;
	}

	public ForumDao getForumDao() {
		return forumDao.get();
	}

	public CommState getComm() {
		return comm;
	}

	@Override
	public KeyPair getKeys() {
		return keys;
	}
}
