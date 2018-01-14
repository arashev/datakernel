package io.datakernel.ot;

import com.google.gson.TypeAdapter;
import io.datakernel.annotation.Nullable;
import io.datakernel.async.Stages;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.jmx.EventloopJmxMBean;
import io.datakernel.jmx.JmxAttribute;
import io.datakernel.jmx.JmxOperation;
import io.datakernel.jmx.StageStats;
import io.datakernel.utils.GsonAdapters;
import io.datakernel.utils.JsonException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;

import static io.datakernel.jmx.ValueStats.SMOOTHING_WINDOW_5_MINUTES;
import static io.datakernel.util.CollectionUtils.difference;
import static io.datakernel.util.CollectionUtils.union;
import static io.datakernel.util.Preconditions.checkState;
import static io.datakernel.utils.GsonAdapters.indent;
import static io.datakernel.utils.GsonAdapters.ofList;
import static java.util.Collections.nCopies;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.*;

public class OTRemoteSql<D> implements OTRemote<Integer, D>, EventloopJmxMBean {
	private final Logger logger = LoggerFactory.getLogger(getClass());
	public static final double DEFAULT_SMOOTHING_WINDOW = SMOOTHING_WINDOW_5_MINUTES;
	public static final String DEFAULT_REVISION_TABLE = "ot_revision";
	public static final String DEFAULT_DIFFS_TABLE = "ot_diffs";
	public static final String DEFAULT_BACKUP_TABLE = "ot_revisions_backup";

	private final Eventloop eventloop;
	private final ExecutorService executor;
	private final OTSystem<D> otSystem;

	private final DataSource dataSource;
	private final TypeAdapter<List<D>> diffsAdapter;

	private String tableRevision = DEFAULT_REVISION_TABLE;
	private String tableDiffs = DEFAULT_DIFFS_TABLE;
	private String tableBackup = DEFAULT_BACKUP_TABLE;

	private String createdBy = null;

	private final StageStats statsCreateCommitId = StageStats.create(DEFAULT_SMOOTHING_WINDOW);
	private final StageStats statsPush = StageStats.create(DEFAULT_SMOOTHING_WINDOW);
	private final StageStats statsGetHeads = StageStats.create(DEFAULT_SMOOTHING_WINDOW);
	private final StageStats statsLoadCommit = StageStats.create(DEFAULT_SMOOTHING_WINDOW);
	private final StageStats statsIsSnapshot = StageStats.create(DEFAULT_SMOOTHING_WINDOW);
	private final StageStats statsLoadSnapshot = StageStats.create(DEFAULT_SMOOTHING_WINDOW);
	private final StageStats statsSaveSnapshot = StageStats.create(DEFAULT_SMOOTHING_WINDOW);

	private OTRemoteSql(Eventloop eventloop, ExecutorService executor, OTSystem<D> otSystem, TypeAdapter<List<D>> diffsAdapter,
	                    DataSource dataSource) {
		this.eventloop = eventloop;
		this.executor = executor;
		this.otSystem = otSystem;
		this.dataSource = dataSource;
		this.diffsAdapter = diffsAdapter;
	}

	public static <D> OTRemoteSql<D> create(Eventloop eventloop, ExecutorService executor, DataSource dataSource, OTSystem<D> otSystem, TypeAdapter<D> diffAdapter) {
		TypeAdapter<List<D>> listAdapter = indent(ofList(diffAdapter), "\t");
		return new OTRemoteSql<>(eventloop, executor, otSystem, listAdapter, dataSource);
	}

	public OTRemoteSql<D> withCreatedBy(String createdBy) {
		this.createdBy = createdBy;
		return this;
	}

	public OTRemoteSql<D> withCustomTableNames(String tableRevision, String tableDiffs, @Nullable String tableBackup) {
		this.tableRevision = tableRevision;
		this.tableDiffs = tableDiffs;
		this.tableBackup = tableBackup;
		return this;
	}

	public DataSource getDataSource() {
		return dataSource;
	}

	public TypeAdapter<List<D>> getDiffsAdapter() {
		return diffsAdapter;
	}

	private String sql(String sql) {
		return sql
				.replace(DEFAULT_REVISION_TABLE, tableRevision)
				.replace(DEFAULT_DIFFS_TABLE, tableDiffs)
				.replace(DEFAULT_BACKUP_TABLE, Objects.toString(tableBackup, ""));
	}

	public void truncateTables() throws SQLException {
		logger.trace("Truncate tables");
		try (Connection connection = dataSource.getConnection()) {
			Statement statement = connection.createStatement();
			statement.execute(sql("TRUNCATE TABLE ot_diffs"));
			statement.execute(sql("TRUNCATE TABLE ot_revisions"));
		}
	}

	@Override
	public CompletionStage<Integer> createCommitId() {
		return statsCreateCommitId.monitor(eventloop.callExecutor(executor, () -> {
			logger.trace("Start Create id");
			try (Connection connection = dataSource.getConnection()) {
				connection.setAutoCommit(true);
				try (PreparedStatement statement = connection.prepareStatement(
						sql("INSERT INTO ot_revisions(type, created_by) VALUES (?, ?)"),
						Statement.RETURN_GENERATED_KEYS)) {
					statement.setString(1, "NEW");
					statement.setString(2, createdBy);
					statement.executeUpdate();
					ResultSet generatedKeys = statement.getGeneratedKeys();
					generatedKeys.next();
					int id = generatedKeys.getInt(1);
					logger.trace("Id created: {}", id);
					return id;
				}
			}
		}));
	}

	private String toJson(List<D> diffs) throws JsonException {
		return GsonAdapters.toJson(diffsAdapter, diffs);
	}

	@SuppressWarnings("unchecked")
	private List<D> fromJson(String json) throws JsonException {
		return GsonAdapters.fromJson(diffsAdapter, json);
	}

	private static String in(int n) {
		return nCopies(n, "?").stream().collect(joining(", ", "(", ")"));
	}

	public CompletionStage<Void> push(OTCommit<Integer, D> commit) {
		return push(singletonList(commit));
	}

	@Override
	public CompletionStage<Void> push(Collection<OTCommit<Integer, D>> commits) {
		if (commits.isEmpty()) return Stages.of(null);
		return statsPush.monitor(eventloop.callExecutor(executor, () -> {
			logger.trace("Push {} commits: {}", commits.size(),
					commits.stream().map(OTCommit::idsToString).collect(toList()));

			try (Connection connection = dataSource.getConnection()) {
				connection.setAutoCommit(false);

				for (OTCommit<Integer, D> commit : commits) {
					for (Integer parentId : commit.getParents().keySet()) {
						List<D> diff = commit.getParents().get(parentId);
						try (PreparedStatement ps = connection.prepareStatement(
								sql("INSERT INTO ot_diffs(revision_id, parent_id, diff) VALUES (?, ?, ?)"))) {
							ps.setInt(1, commit.getId());
							ps.setInt(2, parentId);
							ps.setString(3, toJson(diff));
							ps.executeUpdate();
						}
					}
				}

				Set<Integer> commitIds = commits.stream().map(OTCommit::getId).collect(toSet());
				Set<Integer> commitsParentIds = commits.stream().flatMap(commit -> commit.getParents().keySet().stream()).collect(toSet());
				Set<Integer> headCommitIds = difference(commitIds, commitsParentIds);
				Set<Integer> innerCommitIds = union(commitsParentIds, difference(commitIds, headCommitIds));

				if (!headCommitIds.isEmpty()) {
					try (PreparedStatement ps = connection.prepareStatement(
							sql("UPDATE ot_revisions SET type='HEAD' WHERE type='NEW' AND id IN " + in(headCommitIds.size())))) {
						int pos = 1;
						for (Integer id : headCommitIds) {
							ps.setInt(pos++, id);
						}
						ps.executeUpdate();
					}
				}

				if (!innerCommitIds.isEmpty()) {
					try (PreparedStatement ps = connection.prepareStatement(
							sql("UPDATE ot_revisions SET type='INNER' WHERE id IN " + in(innerCommitIds.size())))) {
						int pos = 1;
						for (Integer id : innerCommitIds) {
							ps.setInt(pos++, id);
						}
						ps.executeUpdate();
					}
				}

				connection.commit();
				logger.trace("{} commits pushed: {}", commits.size(),
						commits.stream().map(OTCommit::idsToString).collect(toList()));
			}
			return null;
		}));
	}

	@Override
	public CompletionStage<Set<Integer>> getHeads() {
		return statsGetHeads.monitor(eventloop.callExecutor(executor, () -> {
			logger.trace("Get Heads");
			try (Connection connection = dataSource.getConnection()) {
				try (PreparedStatement ps = connection.prepareStatement(
						sql("SELECT id FROM ot_revisions WHERE type='HEAD'"))) {
					ResultSet resultSet = ps.executeQuery();
					Set<Integer> result = new HashSet<>();
					while (resultSet.next()) {
						int id = resultSet.getInt(1);
						result.add(id);
					}
					logger.trace("Current heads: {}", result);
					return result;
				}
			}
		}));
	}

	@Override
	public CompletionStage<List<D>> loadSnapshot(Integer revisionId) {
		logger.trace("Load snapshot: {}", revisionId);
		return statsLoadSnapshot.monitor(eventloop.callExecutor(executor, () -> {
			try (Connection connection = dataSource.getConnection()) {
				try (PreparedStatement ps = connection.prepareStatement(
						sql("SELECT snapshot FROM ot_revisions WHERE id=?"))) {
					ps.setInt(1, revisionId);
					ResultSet resultSet = ps.executeQuery();

					if (!resultSet.next()) throw new IOException("No snapshot for id: " + revisionId);

					String str = resultSet.getString(1);
					List<D> snapshot = str == null ? Collections.emptyList() : fromJson(str);
					logger.trace("Snapshot loaded: {}", revisionId);
					return otSystem.squash(snapshot);
				}
			}
		}));
	}

	@Override
	public CompletionStage<OTCommit<Integer, D>> loadCommit(Integer revisionId) {
		return statsLoadCommit.monitor(eventloop.callExecutor(executor, () -> {
			logger.trace("Start load commit: {}", revisionId);
			try (Connection connection = dataSource.getConnection()) {
				Map<Integer, List<D>> parentDiffs = new HashMap<>();

				long timestamp = 0;
				boolean snapshot = false;

				try (PreparedStatement ps = connection.prepareStatement(
						sql("SELECT UNIX_TIMESTAMP(ot_revisions.timestamp) AS timestamp, ot_revisions.snapshot IS NOT NULL AS snapshot, ot_diffs.parent_id, ot_diffs.diff " +
								"FROM ot_revisions " +
								"LEFT JOIN ot_diffs ON ot_diffs.revision_id=ot_revisions.id " +
								"WHERE ot_revisions.id=?"))) {
					ps.setInt(1, revisionId);
					ResultSet resultSet = ps.executeQuery();

					while (resultSet.next()) {
						timestamp = resultSet.getLong(1) * 1000L;
						snapshot = resultSet.getBoolean(2);
						int parentId = resultSet.getInt(3);
						String diffString = resultSet.getString(4);
						if (diffString != null) {
							List<D> diff = fromJson(diffString);
							parentDiffs.put(parentId, diff);
						}
					}
				}

				if (timestamp == 0) {
					throw new IOException("No commit with id: " + revisionId);
				}

				logger.trace("Finish load commit: {}, parentIds: {}", revisionId, parentDiffs.keySet());
				return OTCommit.of(revisionId, parentDiffs).withCommitMetadata(timestamp, snapshot);
			}
		}));
	}

	@Override
	public CompletionStage<Void> saveSnapshot(Integer revisionId, List<D> diffs) {
		return statsSaveSnapshot.monitor(eventloop.callExecutor(executor, () -> {
			logger.trace("Start save snapshot: {}, diffs: {}", revisionId, diffs.size());
			try (Connection connection = dataSource.getConnection()) {
				String snapshot = toJson(otSystem.squash(diffs));
				try (PreparedStatement ps = connection.prepareStatement(sql("" +
						"UPDATE ot_revisions " +
						"SET snapshot = ? " +
						"WHERE id = ?"))) {
					ps.setString(1, snapshot);
					ps.setInt(2, revisionId);
					ps.executeUpdate();
					logger.trace("Finish save snapshot: {}, diffs: {}", revisionId, diffs.size());
					return null;
				}
			}
		}));
	}

	@Override
	public CompletionStage<Void> cleanup(Integer minId) {
		return eventloop.callExecutor(executor, () -> {
			logger.trace("Start cleanup: {}", minId);
			try (Connection connection = dataSource.getConnection()) {
				connection.setAutoCommit(false);

				try (PreparedStatement ps = connection.prepareStatement(
						sql("DELETE FROM ot_revisions WHERE id < ?"))) {
					ps.setInt(1, minId);
					ps.executeUpdate();
				}

				try (PreparedStatement ps = connection.prepareStatement(
						sql("DELETE FROM ot_diffs WHERE revision_id < ?"))) {
					ps.setInt(1, minId);
					ps.executeUpdate();
				}

				connection.commit();
				logger.trace("Finish cleanup: {}", minId);
			}

			return null;
		});
	}

	@Override
	public CompletionStage<Void> backup(Integer checkpointId, List<D> diffs) {
		checkState(this.tableBackup != null);
		return eventloop.callExecutor(executor, () -> {
			try (Connection connection = dataSource.getConnection()) {
				try (PreparedStatement statement = connection.prepareStatement(
						sql("INSERT INTO ot_revisions_backup(id, snapshot) VALUES (?, ?)"))) {
					statement.setInt(1, checkpointId);
					statement.setString(2, toJson(diffs));
					statement.executeUpdate();
					return null;
				}
			}
		});
	}

	@Override
	public Eventloop getEventloop() {
		return eventloop;
	}

	@JmxAttribute
	public StageStats getStatsCreateCommitId() {
		return statsCreateCommitId;
	}

	@JmxAttribute
	public StageStats getStatsPush() {
		return statsPush;
	}

	@JmxAttribute
	public StageStats getStatsGetHeads() {
		return statsGetHeads;
	}

	@JmxAttribute
	public StageStats getStatsLoadCommit() {
		return statsLoadCommit;
	}

	@JmxAttribute
	public StageStats getStatsIsSnapshot() {
		return statsIsSnapshot;
	}

	@JmxAttribute
	public StageStats getStatsLoadSnapshot() {
		return statsLoadSnapshot;
	}

	@JmxAttribute
	public StageStats getStatsSaveSnapshot() {
		return statsSaveSnapshot;
	}

	@JmxOperation
	public void resetStats() {
		statsCreateCommitId.resetStats();
		statsPush.resetStats();
		statsGetHeads.resetStats();
		statsLoadCommit.resetStats();
		statsIsSnapshot.resetStats();
		statsLoadSnapshot.resetStats();
		statsSaveSnapshot.resetStats();
	}

}
