package io.global.forum.pojo;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static io.datakernel.util.Preconditions.checkArgument;
import static io.datakernel.util.StringFormatUtils.limit;

public final class Post {
	private final List<Post> children = new ArrayList<>();
	@NotNull
	private final UserId author;
	private final long initialTimestamp;
	private final Set<UserId> likes = new HashSet<>();
	private final Set<UserId> dislikes = new HashSet<>();
	private final Map<String, Attachment> attachments = new HashMap<>();

	@Nullable
	private Post parent;
	private String content = "";
	@Nullable
	private UserId deletedBy;
	private long lastEditTimestamp = -1;

	private Post(@NotNull UserId author, long initialTimestamp) {
		this.author = author;
		this.initialTimestamp = initialTimestamp;
	}

	public static Post create(@NotNull UserId author, long initialTimestamp) {
		return new Post(author, initialTimestamp);
	}

	// Mutates child
	public void addChild(@NotNull Post child) {
		checkArgument(child.parent == null, "Child has other parent");
		this.children.add(child);
		child.parent = this;
	}

	public void removeChild(@NotNull Post child) {
		this.children.remove(child);
	}

	public void setContent(@NotNull String content) {
		this.content = content;
	}

	public void addAttachment(String globalFsId, Attachment attachment) {
		this.attachments.put(globalFsId, attachment);
	}

	public void removeAttachment(String globalFsId) {
		this.attachments.remove(globalFsId);
	}

	public void setLastEditTimestamp(long lastEditTimestamp) {
		this.lastEditTimestamp = lastEditTimestamp;
	}

	public void addLike(UserId userId) {
		likes.add(userId);
		dislikes.remove(userId); // if any
	}

	public void addDislike(UserId userId) {
		dislikes.add(userId);
		likes.remove(userId); // if any
	}

	public void removeLikeAndDislike(UserId userId) {
		likes.remove(userId);
		dislikes.remove(userId);
	}

	public void delete(@NotNull UserId deletedBy) {
		this.deletedBy = deletedBy;
	}

	public void unDelete() {
		this.deletedBy = null;
	}

	// region getters
	@NotNull
	public UserId getAuthor() {
		return author;
	}

	public long getInitialTimestamp() {
		return initialTimestamp;
	}

	@NotNull
	public Map<String, Attachment> getAttachments() {
		return attachments;
	}

	@NotNull
	public String getContent() {
		return content;
	}

	public long getLastEditTimestamp() {
		return lastEditTimestamp;
	}

	@Nullable
	public UserId getDeletedBy() {
		return deletedBy;
	}

	@Nullable
	public Post getParent() {
		return parent;
	}

	public List<Post> getChildren() {
		return children;
	}

	public Set<UserId> getLikes() {
		return likes;
	}

	public Set<UserId> getDislikes() {
		return dislikes;
	}
	// endregion

	@Override
	@SuppressWarnings("ConstantConditions")
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Post post = (Post) o;

		if (initialTimestamp != post.initialTimestamp) return false;
		if (lastEditTimestamp != post.lastEditTimestamp) return false;
		if (!children.equals(post.children)) return false;
		if (!author.equals(post.author)) return false;
		if (!likes.equals(post.likes)) return false;
		if (!dislikes.equals(post.dislikes)) return false;
		if (parent != null ? !parent.equals(post.parent) : post.parent != null) return false;
		if (!content.equals(post.content)) return false;
		if (!attachments.equals(post.attachments)) return false;
		if (deletedBy != null ? !deletedBy.equals(post.deletedBy) : post.deletedBy != null) return false;

		return true;
	}

	@Override
	public int hashCode() {
		int result = children.hashCode();
		result = 31 * result + author.hashCode();
		result = 31 * result + (int) (initialTimestamp ^ (initialTimestamp >>> 32));
		result = 31 * result + likes.hashCode();
		result = 31 * result + dislikes.hashCode();
		result = 31 * result + (parent != null ? parent.hashCode() : 0);
		result = 31 * result + content.hashCode();
		result = 31 * result + attachments.hashCode();
		result = 31 * result + (deletedBy != null ? deletedBy.hashCode() : 0);
		result = 31 * result + (int) (lastEditTimestamp ^ (lastEditTimestamp >>> 32));
		return result;
	}

	@Override
	public String toString() {
		return "Post{" +
				"children=" + children +
				", author=" + author +
				", initialTimestamp=" + initialTimestamp +
				", likes=" + likes +
				", dislikes=" + dislikes +
				", parent=" + parent +
				", content='" + limit(content, 20) + '\'' +
				", attachments=" + attachments +
				", deletedBy=" + deletedBy +
				", lastEditTimestamp=" + lastEditTimestamp +
				'}';
	}
}
