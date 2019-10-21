package io.datakernel.di.core;

import io.datakernel.di.annotation.EagerSingleton;
import io.datakernel.di.impl.*;
import io.datakernel.di.module.*;
import io.datakernel.di.util.Trie;
import io.datakernel.di.util.Types;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static io.datakernel.di.core.BindingGenerator.REFUSING;
import static io.datakernel.di.core.BindingGenerator.combinedGenerator;
import static io.datakernel.di.core.BindingTransformer.IDENTITY;
import static io.datakernel.di.core.BindingTransformer.combinedTransformer;
import static io.datakernel.di.core.Multibinder.ERROR_ON_DUPLICATE;
import static io.datakernel.di.core.Multibinder.combinedMultibinder;
import static io.datakernel.di.core.Scope.UNSCOPED;
import static io.datakernel.di.impl.CompiledBinding.missingOptionalBinding;
import static io.datakernel.di.module.BindingSet.BindingType.COMMON;
import static io.datakernel.di.module.BindingSet.BindingType.EAGER;
import static io.datakernel.di.util.Utils.getScopeDisplayString;
import static io.datakernel.di.util.Utils.next;
import static java.util.Collections.*;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

/**
 * Injector is the main working component of the DataKernel DI.
 * <p>
 * It stores a trie of binding graphs and a cache of already made singletons.
 * <p>
 * Each injector is associated with exactly zero or one instance per {@link Key}.
 * <p>
 * Injector uses binding graph at the root of the trie to recursively create and then store instances of objects
 * associated with some {@link Key keys}.
 * Branches of the trie are used to {@link #enterScope enter scopes}.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public final class Injector {
	public static final Key<Set<InstanceInjector<?>>> INSTANCE_INJECTORS_KEY = new Key<Set<InstanceInjector<?>>>() {};

	private static final class DependencyGraph {
		final Scope[] scope;
		final Map<Key<?>, Binding<?>> bindings;
		final Map<Key<?>, CompiledBinding<?>> compiledBindings;
		final CompiledBinding<?>[] eagerSingletons;
		final Map<Key<?>, Integer> compiledIndices;
		final int size;

		private DependencyGraph(Scope[] scope, Map<Key<?>, Binding<?>> bindings, Map<Key<?>, CompiledBinding<?>> compiledBindings, CompiledBinding<?>[] eagerSingletons, Map<Key<?>, Integer> compiledIndices, int size) {
			this.scope = scope;
			this.bindings = bindings;
			this.compiledBindings = compiledBindings;
			this.eagerSingletons = eagerSingletons;
			this.compiledIndices = compiledIndices;
			this.size = size;
		}
	}

	final Map<Key<?>, CompiledBinding<?>> compiledBindings;
	final Map<Key<?>, Integer> compiledIndices;
	final AtomicReferenceArray[] scopedInstances;
	final Trie<Scope, DependencyGraph> scopeTree;
	@Nullable
	final Injector parent;

	@SuppressWarnings("unchecked")
	private Injector(@Nullable Injector parent, Trie<Scope, DependencyGraph> scopeTree) {
		this.parent = parent;
		this.scopeTree = scopeTree;

		DependencyGraph localGraph = scopeTree.get();

		this.compiledBindings = localGraph.compiledBindings;
		this.compiledIndices = localGraph.compiledIndices;

		AtomicReferenceArray[] scopedInstances = parent == null ?
				new AtomicReferenceArray[1] :
				Arrays.copyOf(parent.scopedInstances, parent.scopedInstances.length + 1);

		scopedInstances[scopedInstances.length - 1] = new AtomicReferenceArray(localGraph.size);
		scopedInstances[scopedInstances.length - 1].set(0, this);

		this.scopedInstances = scopedInstances;

		for (CompiledBinding<?> compiledBinding : localGraph.eagerSingletons) {
			compiledBinding.getInstance(scopedInstances, -1);
		}
	}

	/**
	 * This constructor combines given modules (along with a {@link DefaultModule})
	 * and then {@link #compile(Injector, Module) compiles} them.
	 */
	public static Injector of(Module... modules) {
		return compile(null, Modules.combine(Modules.combine(modules), new DefaultModule()));
	}

	public static Injector of(@Nullable Injector parent, Module... modules) {
		return compile(parent, Modules.combine(Modules.combine(modules), new DefaultModule()));
	}

	/**
	 * This constructor is a shortcut for threadsafe {@link #compile(Injector, Scope[], Trie, Multibinder, BindingTransformer, BindingGenerator) compile}
	 * with no instance overrides and no multibinders, transformers or generators.
	 */
	public static Injector of(@NotNull Trie<Scope, Map<Key<?>, Binding<?>>> bindings) {
		return compile(null, UNSCOPED,
				bindings.map(map -> map.entrySet().stream().collect(toMap(Entry::getKey, entry -> new BindingSet<>(singleton(entry.getValue()), COMMON)))),
				ERROR_ON_DUPLICATE,
				IDENTITY,
				REFUSING);
	}

	/**
	 * This constructor threadsafely {@link #compile(Injector, Scope[], Trie, Multibinder, BindingTransformer, BindingGenerator) compiles}
	 * given module, extracting bindings and their multibinders, transformers and generators from it, with no instance overrides
	 */
	public static Injector compile(@Nullable Injector parent, Module module) {
		return compile(parent, UNSCOPED, module.getBindings(),
				combinedMultibinder(module.getMultibinders()),
				combinedTransformer(module.getBindingTransformers()),
				combinedGenerator(module.getBindingGenerators()));
	}

	/**
	 * The most full-fledged compile method that allows you to create an Injector of any configuration.
	 * <p>
	 * Note that any injector <b>always</b> sets a binding of Injector key to provide itself.
	 *
	 * @param parent           parent injector that is called when this injector cannot fulfill the request
	 * @param scope            the scope of the injector, can be described as 'prefix of the root' of the binding trie,
	 *                         used when {@link #enterScope entering scopes}
	 * @param bindingsMultimap a trie of binding set graph with multiple possible conflicting bindings per key
	 *                         that are resolved as part of the compilation.
	 * @param multibinder      a multibinder that is called on every binding conflict (see {@link Multibinder#combinedMultibinder})
	 * @param transformer      a transformer that is called on every binding once (see {@link BindingTransformer#combinedTransformer})
	 * @param generator        a generator that is called on every missing binding (see {@link BindingGenerator#combinedGenerator})
	 * @see #enterScope
	 */
	public static Injector compile(@Nullable Injector parent,
			Scope[] scope,
			@NotNull Trie<Scope, Map<Key<?>, BindingSet<?>>> bindingsMultimap,
			@NotNull Multibinder<?> multibinder,
			@NotNull BindingTransformer<?> transformer,
			@NotNull BindingGenerator<?> generator) {

		Trie<Scope, Map<Key<?>, Binding<?>>> bindings = Preprocessor.reduce(bindingsMultimap, multibinder, transformer, generator);

		Set<Key<?>> known = new HashSet<>();
		known.add(Key.of(Injector.class)); // injector is hardcoded in and will always be present
		if (parent != null) {
			known.addAll(parent.compiledBindings.keySet());
		}

		Preprocessor.check(known, bindings);

		return new Injector(parent, compileBindingsTrie(
				parent != null ? parent.scopedInstances.length : 0,
				UNSCOPED,
				bindings,
				bindingsMultimap,
				parent != null ? parent.compiledBindings : emptyMap()
		));
	}

	protected static Trie<Scope, DependencyGraph> compileBindingsTrie(int scope, Scope[] path,
			Trie<Scope, Map<Key<?>, Binding<?>>> bindingsTrie,
			Trie<Scope, Map<Key<?>, BindingSet<?>>> bindingsMultimap,
			Map<Key<?>, CompiledBinding<?>> compiledBindingsOfParent) {

		DependencyGraph dependencyGraph = compileBindings(scope, path, bindingsTrie.get(), bindingsMultimap.get(), compiledBindingsOfParent);

		Map<Scope, Trie<Scope, DependencyGraph>> children = new HashMap<>();

		bindingsTrie.getChildren().forEach((childScope, trie) -> {
			Map<Key<?>, CompiledBinding<?>> compiledBindingsCopy = new HashMap<>(compiledBindingsOfParent);
			compiledBindingsCopy.putAll(dependencyGraph.compiledBindings);
			children.put(childScope, compileBindingsTrie(scope + 1, next(path, childScope), bindingsTrie.get(childScope), bindingsMultimap.get(childScope), compiledBindingsCopy));
		});

		return new Trie<>(dependencyGraph, children);
	}

	protected static DependencyGraph compileBindings(int scope, Scope[] path,
			Map<Key<?>, Binding<?>> bindings,
			Map<Key<?>, BindingSet<?>> bindingSets,
			Map<Key<?>, CompiledBinding<?>> compiledBindingsOfParent) {
		boolean threadsafe = path.length == 0 || path[path.length - 1].isThreadsafe();
		Map<Key<?>, CompiledBinding<?>> compiledBindings = new HashMap<>();
		Map<Key<?>, Integer> compiledIndexes = new HashMap<>();
		compiledBindings.put(Key.of(Injector.class),
				scope == 0 ?
						new CompiledBinding<Object>() {
							volatile Object instance;

							@Override
							public Object getInstance(AtomicReferenceArray[] scopedInstances, int synchronizedScope) {
								Object instance = this.instance;
								if (instance != null) return instance;
								this.instance = scopedInstances[scope].get(0);
								return this.instance;
							}
						} :
						new CompiledBinding<Object>() {
							@Override
							public Object getInstance(AtomicReferenceArray[] scopedInstances, int synchronizedScope) {
								return scopedInstances[scope].get(0);
							}
						});
		compiledIndexes.put(Key.of(Injector.class), 0);
		int[] nextIndex = {1};

		List<CompiledBinding<?>> eagerSingletons = new ArrayList<>();

		for (Entry<Key<?>, Binding<?>> e : bindings.entrySet()) {

			CompiledBinding<?> compiledBinding = compileBinding(
					scope, path,
					threadsafe, e.getKey(),
					bindings, compiledBindingsOfParent, compiledBindings,
					compiledIndexes, nextIndex
			);

			BindingSet<?> bindingSet = bindingSets.get(e.getKey());
			if (bindingSet != null && bindingSet.getType() == EAGER) {
				eagerSingletons.add(compiledBinding);
			}
		}

		bindings.put(Key.of(Injector.class), Binding.to(() -> {
			throw new AssertionError("Injector constructor must never be called since it's instance is always put in the cache manually");
		}));
		compiledBindingsOfParent.forEach(compiledBindings::putIfAbsent);
		int size = nextIndex[0];
		nextIndex[0] = -1;
		return new DependencyGraph(path, bindings, compiledBindings, eagerSingletons.toArray(new CompiledBinding[0]), compiledIndexes, size);
	}

	private static CompiledBinding<?> compileBinding(
			int scope, Scope[] path,
			boolean threadsafe, Key<?> key,
			Map<Key<?>, Binding<?>> bindings,
			Map<Key<?>, CompiledBinding<?>> compiledBindingsOfParent,
			Map<Key<?>, CompiledBinding<?>> compiledBindings,
			Map<Key<?>, Integer> compiledIndexes, int[] nextIndex
	) {

		// not computeIfAbsent because of recursion
		CompiledBinding<?> already = compiledBindings.get(key);
		if (already != null) {
			return already;
		}
		if (nextIndex[0] == -1) {
			throw new DIException("Failed to locate a binding for " + key.getDisplayString() + " after scope " + getScopeDisplayString(path) + " was fully compiled");
		}

		Binding<?> binding = bindings.get(key);

		if (binding == null) {
			CompiledBinding<?> compiled = compiledBindingsOfParent.getOrDefault(key, missingOptionalBinding());
			compiledBindings.put(key, compiled);
			return compiled;
		}

		BindingCompiler<?> compiler = binding.getCompiler();

		Integer index;
		if (binding.isCached() && !(compiler instanceof PlainCompiler)) {
			compiledIndexes.put(key, index = nextIndex[0]++);
		} else {
			index = null;
		}

		CompiledBinding<?> compiled = compiler.compile(
				new CompiledBindingLocator() {
					@SuppressWarnings("unchecked")
					@Override
					public @NotNull <Q> CompiledBinding<Q> get(Key<Q> key) {
						return (CompiledBinding<Q>) compileBinding(
								scope,
								path,
								threadsafe,
								key,
								bindings,
								compiledBindingsOfParent,
								compiledBindings,
								compiledIndexes,
								nextIndex
						);
					}
				}, threadsafe, scope, index);

		compiledBindings.put(key, compiled);
		return compiled;
	}

	/**
	 * @see #getInstance(Key)
	 */
	@NotNull
	public <T> T getInstance(@NotNull Class<T> type) {
		return getInstance(Key.ofType(type));
	}

	@SuppressWarnings("unchecked")
	@NotNull
	public <T> T getInstance(@NotNull Key<T> key) {
		CompiledBinding<?> binding = compiledBindings.get(key);
		if (binding != null) {
			return (T) binding.getInstance(scopedInstances, -1);
		}
		throw DIException.cannotConstruct(key, null);
	}

	/**
	 * @see #getInstanceOrNull(Key)
	 */
	@Nullable
	public <T> T getInstanceOrNull(@NotNull Class<T> type) {
		return getInstanceOrNull(Key.of(type));
	}

	@SuppressWarnings("unchecked")
	@Nullable
	public <T> T getInstanceOrNull(@NotNull Key<T> key) {
		CompiledBinding<?> binding = compiledBindings.get(key);
		return binding != null ? (T) binding.getInstance(scopedInstances, -1) : null;
	}

	/**
	 * @see #getInstanceOr(Key, Object)
	 */
	public <T> T getInstanceOr(@NotNull Class<T> type, T defaultValue) {
		return getInstanceOr(Key.of(type), defaultValue);
	}

	/**
	 * Same as {@link #getInstanceOrNull(Key)}, but replaces <code>null</code> with given default value.
	 */
	public <T> T getInstanceOr(@NotNull Key<T> key, T defaultValue) {
		T instance = getInstanceOrNull(key);
		return instance != null ? instance : defaultValue;
	}

	@Nullable
	public <T> T peekInstance(@NotNull Class<T> type) {
		return peekInstance(Key.of(type));
	}

	/**
	 * This method returns an instance only if it already was created by a {@link #getInstance} call before,
	 * it does not trigger instance creation.
	 */
	@Nullable
	@SuppressWarnings("unchecked")
	public <T> T peekInstance(@NotNull Key<T> key) {
		Integer index = compiledIndices.get(key);
		if (index != null) {
			return (T) scopedInstances[scopedInstances.length - 1].get(index);
		}
		throw DIException.noCachedBidning(key, getScope());
	}

	@NotNull
	public <T> InstanceProvider<T> getInstanceProvider(@NotNull Class<T> type) {
		return getInstanceProvider(Key.of(type));
	}

	@NotNull
	public <T> InstanceProvider<T> getInstanceProvider(@NotNull Key<T> key) {
		return getInstance(Key.ofType(Types.parameterized(InstanceProvider.class, key.getType()), key.getName()));
	}

	@NotNull
	public <T> InstanceInjector<T> getInstanceInjector(@NotNull Class<T> type) {
		return getInstanceInjector(Key.of(type));
	}

	@NotNull
	public <T> InstanceInjector<T> getInstanceInjector(@NotNull Key<T> key) {
		return getInstance(Key.ofType(Types.parameterized(InstanceInjector.class, key.getType()), key.getName()));
	}

	/**
	 * This method checks if an instance for this key was created by a {@link #getInstance} call before.
	 */
	public boolean hasInstance(@NotNull Class<?> type) {
		return hasInstance(Key.of(type));
	}

	/**
	 * This method checks if an instance for this key was created by a {@link #getInstance} call before.
	 */
	public boolean hasInstance(@NotNull Key<?> key) {
		Integer index = compiledIndices.get(key);
		if (index != null) {
			return scopedInstances[scopedInstances.length - 1].get(index) != null;
		}
		throw DIException.noCachedBidning(key, getScope());
	}

	/**
	 * This method returns a copy of the injector cache - a map of all already created instances.
	 */
	public Map<Key<?>, Object> peekInstances() {
		Map<Key<?>, Object> result = new HashMap<>();
		for (Entry<Key<?>, Integer> entry : compiledIndices.entrySet()) {
			Key<?> key = entry.getKey();
			Integer index = entry.getValue();
			Object value = scopedInstances[scopedInstances.length - 1].get(index);
			if (value != null) {
				result.put(key, value);
			}
		}
		return result;
	}

	public Set<Key<?>> getBindings() {
		return compiledIndices.keySet();
	}

	public Set<Key<?>> getAllBindings() {
		return compiledBindings.keySet();
	}

	public <T> void putInstance(Class<T> key, T instance) {
		putInstance(Key.of(key), instance);
	}

	@SuppressWarnings("unchecked")
	public <T> void putInstance(Key<T> key, T instance) {
		Integer index = compiledIndices.get(key);
		if (index == null) {
			throw DIException.noCachedBidning(key, getScope());
		}
		scopedInstances[scopedInstances.length - 1].set(index, instance);
	}

	/**
	 * This method triggers creation of all keys that were marked as {@link EagerSingleton eager singletons}.
	 *
	 * @see EagerSingleton
	 */
	public Set<Key<?>> createEagerSingletons() {
		Set<Key<?>> eagerSingletons = getInstanceOr(new Key<Set<Key<?>>>(EagerSingleton.class) {}, emptySet());
		eagerSingletons.forEach(this::getInstanceOrNull); // orNull because bindings for some keys could be provided in scopes
		return eagerSingletons;
	}

	/**
	 * The key of type Set&lt;InstanceInjector&lt;?&gt;&gt; (note the wildcard type) is treated specially by this method,
	 * it calls all of the instance injectors the set contains on instances of their respective keys, if such instances
	 * were already made by this injector.
	 *
	 * @see AbstractModule#postInjectInto(Key)
	 */
	@SuppressWarnings({"unchecked", "JavadocReference"})
	public Set<Key<?>> postInjectInstances() {
		Set<InstanceInjector<?>> postInjectors = getInstanceOr(INSTANCE_INJECTORS_KEY, emptySet());
		for (InstanceInjector<?> instanceInjector : postInjectors) {
			Object instance = peekInstance(instanceInjector.key());
			if (instance != null) {
				((InstanceInjector<Object>) instanceInjector).injectInto(instance);
			}
		}
		return postInjectors.stream().map(InstanceInjector::key).collect(toSet());
	}

	@Nullable
	public Injector getParent() {
		return parent;
	}

	/**
	 * Returns the scope this injector operates upon.
	 * Scopes can be nested and this method returns a path
	 * for the binding graph trie as an array of trie prefixes.
	 */
	public Scope[] getScope() {
		return scopeTree.get().scope;
	}

	public Trie<Scope, Map<Key<?>, Binding<?>>> getBindingsTrie() {
		return scopeTree.map(graph -> graph.bindings);
	}

	@Nullable
	public <T> Binding<T> getBinding(Class<T> type) {
		return getBinding(Key.of(type));
	}

	@SuppressWarnings("unchecked")
	@Nullable
	public <T> Binding<T> getBinding(Key<T> key) {
		return (Binding<T>) getBindingsTrie().get().get(key);
	}

	public boolean hasBinding(Class<?> type) {
		return hasBinding(Key.of(type));
	}

	public boolean hasBinding(Key<?> key) {
		return compiledBindings.containsKey(key);
	}

	/**
	 * Creates an injector that operates on a binding graph at a given prefix (scope) of the binding graph trie and this injector as its parent.
	 */
	public Injector enterScope(@NotNull Scope scope) {
		return new Injector(this, scopeTree.get(scope));
	}

}
