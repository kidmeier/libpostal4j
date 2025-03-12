package kid_meier.libpostal4j;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Stream;

import kid_meier.libpostal4j.ffi.libpostal_address_parser_response;
import kid_meier.libpostal4j.ffi.libpostal_duplicate_options;
import kid_meier.libpostal4j.ffi.libpostal_fuzzy_duplicate_options;
import kid_meier.libpostal4j.ffi.libpostal_fuzzy_duplicate_status;
import kid_meier.libpostal4j.ffi.libpostal_language_classifier_response;
import kid_meier.libpostal4j.ffi.libpostal_near_dupe_hash_options;
import kid_meier.libpostal4j.ffi.libpostal_normalize_options;
import kid_meier.libpostal4j.ffi.libpostal_normalized_token;
import kid_meier.libpostal4j.ffi.libpostal_token;

import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.MemorySegment.NULL;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import static kid_meier.libpostal4j.ffi.libpostal_h.LIBPOSTAL_EXACT_DUPLICATE;
import static kid_meier.libpostal4j.ffi.libpostal_h.LIBPOSTAL_LIKELY_DUPLICATE;
import static kid_meier.libpostal4j.ffi.libpostal_h.LIBPOSTAL_NON_DUPLICATE;
import static kid_meier.libpostal4j.ffi.libpostal_h.LIBPOSTAL_NULL_DUPLICATE_STATUS;
import static kid_meier.libpostal4j.ffi.libpostal_h.LIBPOSTAL_POSSIBLE_DUPLICATE_NEEDS_REVIEW;
import static kid_meier.libpostal4j.ffi.libpostal_h.free;
import static kid_meier.libpostal4j.ffi.libpostal_h.libpostal_address_parser_response_destroy;
import static kid_meier.libpostal4j.ffi.libpostal_h.libpostal_classify_language;
import static kid_meier.libpostal4j.ffi.libpostal_h.libpostal_expand_address;
import static kid_meier.libpostal4j.ffi.libpostal_h.libpostal_expand_address_root;
import static kid_meier.libpostal4j.ffi.libpostal_h.libpostal_expansion_array_destroy;
import static kid_meier.libpostal4j.ffi.libpostal_h.libpostal_get_address_parser_default_options;
import static kid_meier.libpostal4j.ffi.libpostal_h.libpostal_get_default_duplicate_options;
import static kid_meier.libpostal4j.ffi.libpostal_h.libpostal_get_default_fuzzy_duplicate_options;
import static kid_meier.libpostal4j.ffi.libpostal_h.libpostal_get_default_options;
import static kid_meier.libpostal4j.ffi.libpostal_h.libpostal_get_near_dupe_hash_default_options;
import static kid_meier.libpostal4j.ffi.libpostal_h.libpostal_is_floor_duplicate;
import static kid_meier.libpostal4j.ffi.libpostal_h.libpostal_is_house_number_duplicate;
import static kid_meier.libpostal4j.ffi.libpostal_h.libpostal_is_name_duplicate;
import static kid_meier.libpostal4j.ffi.libpostal_h.libpostal_is_name_duplicate_fuzzy;
import static kid_meier.libpostal4j.ffi.libpostal_h.libpostal_is_po_box_duplicate;
import static kid_meier.libpostal4j.ffi.libpostal_h.libpostal_is_postal_code_duplicate;
import static kid_meier.libpostal4j.ffi.libpostal_h.libpostal_is_street_duplicate;
import static kid_meier.libpostal4j.ffi.libpostal_h.libpostal_is_street_duplicate_fuzzy;
import static kid_meier.libpostal4j.ffi.libpostal_h.libpostal_is_toponym_duplicate;
import static kid_meier.libpostal4j.ffi.libpostal_h.libpostal_is_unit_duplicate;
import static kid_meier.libpostal4j.ffi.libpostal_h.libpostal_language_classifier_response_destroy;
import static kid_meier.libpostal4j.ffi.libpostal_h.libpostal_near_dupe_hashes_languages;
import static kid_meier.libpostal4j.ffi.libpostal_h.libpostal_near_dupe_name_hashes;
import static kid_meier.libpostal4j.ffi.libpostal_h.libpostal_normalized_tokens_languages;
import static kid_meier.libpostal4j.ffi.libpostal_h.libpostal_parse_address;
import static kid_meier.libpostal4j.ffi.libpostal_h.libpostal_place_languages;
import static kid_meier.libpostal4j.ffi.libpostal_h.libpostal_setup_datadir;
import static kid_meier.libpostal4j.ffi.libpostal_h.libpostal_setup_language_classifier_datadir;
import static kid_meier.libpostal4j.ffi.libpostal_h.libpostal_setup_parser_datadir;
import static kid_meier.libpostal4j.ffi.libpostal_h.libpostal_teardown;
import static kid_meier.libpostal4j.ffi.libpostal_h.libpostal_teardown_language_classifier;
import static kid_meier.libpostal4j.ffi.libpostal_h.libpostal_teardown_parser;

public class Libpostal implements AutoCloseable {

	private static final AtomicBoolean LOADED = new AtomicBoolean(false);

	private static MemorySegment DEFAULT_EXPAND_OPTIONS_STRUCT;
	private static MemorySegment DEFAULT_PARSE_OPTIONS_STRUCT;
	private static MemorySegment DEFAULT_NEAR_DUPE_OPTIONS_STRUCT;
	private static MemorySegment DEFAULT_DUPLICATE_OPTIONS_STRUCT;
	private static MemorySegment DEFAULT_FUZZY_DUPLICATE_OPTIONS_STRUCT;

	private Arena arena;
	private BufferPool bufferPool;

	private Libpostal(Arena arena) {
		this.arena = arena;
		this.bufferPool = new BufferPool(arena);
	}

	public static class Options {

		public static final String DEFAULT_DATADIR = "/usr/local/share/libpostal";

		private Supplier<Arena> arenaFactory;
		private Executor initExecutor;
		private String dataDir;

		public Options() {
			this.arenaFactory = Arena::ofConfined;
			this.initExecutor = Runnable::run;
			this.dataDir = DEFAULT_DATADIR;
		}

		public Options withArena(Arena arena) {
			requireNonNull(arena);
			return withArenaFactory(() -> arena);
		}

		public Options withArenaFactory(Supplier<Arena> arenaFactory) {
			this.arenaFactory = requireNonNull(arenaFactory);
			return this;
		}

		public Options withInitExecutor(Executor executor) {
			this.initExecutor = requireNonNull(executor);
			return this;
		}

		public Options withDatadir(String dataDir) {
			this.dataDir = dataDir;
			return this;
		}

	}

	public static Libpostal initialize() {
		return initialize(new Options());
	}

	public static Libpostal initialize(Options options) {
		synchronized (LOADED) {
			if (LOADED.compareAndSet(false, true)) {
				Arena arena = initializeLibrary(options);
				return new Libpostal(arena);
			}
			throw new IllegalStateException("This class is a singleton and has already been initialized by another caller");
		}
	}

	private static Arena initializeLibrary(Options options) {
		try (Arena local = Arena.ofConfined()) {
			MemorySegment datadirCString = local.allocateFrom(options.dataDir);
			if (!libpostal_setup_datadir(datadirCString)) {
				throw new RuntimeException("libpostal_setup_datadir(\"%s\")".formatted(options.dataDir));
			}
			if (!libpostal_setup_parser_datadir(datadirCString)) {
				throw new RuntimeException("libpostal_setup_paser_datadir(\"%s\")".formatted(options.dataDir));
			}
			if (!libpostal_setup_language_classifier_datadir(datadirCString)) {
				throw new RuntimeException("libpostal_setup_language_classifier_datadir(\"%s\")".formatted(options.dataDir));
			}
		}
		Supplier<Arena> arenaFactory = options.arenaFactory;
		CompletableFuture<Arena> arenaFuture = new CompletableFuture<>();
		options.initExecutor.execute(() -> {
			try {
				Arena arena = arenaFactory.get();
				DEFAULT_EXPAND_OPTIONS_STRUCT = libpostal_get_default_options(arena);
				DEFAULT_PARSE_OPTIONS_STRUCT = libpostal_get_address_parser_default_options(arena);
				DEFAULT_NEAR_DUPE_OPTIONS_STRUCT = libpostal_get_near_dupe_hash_default_options(arena);
				DEFAULT_DUPLICATE_OPTIONS_STRUCT = libpostal_get_default_duplicate_options(arena);
				DEFAULT_FUZZY_DUPLICATE_OPTIONS_STRUCT = libpostal_get_default_fuzzy_duplicate_options(arena);
				arenaFuture.complete(arena);
			} catch (Throwable t) {
				arenaFuture.completeExceptionally(t);
			}
		});
		return arenaFuture.join();
	}

	@Override
	public void close() {
		synchronized (LOADED) {
			if (!LOADED.compareAndSet(true, false)) {
				throw new IllegalStateException("close()'d before initialize()'d");
			}
			libpostal_teardown_language_classifier();
			libpostal_teardown_parser();
			libpostal_teardown();
			arena.close();
			arena = null;
			bufferPool = null;
		}
	}

	// ////////////////////////////////////////////////////////////////////////
	// Address expansion
	// ////////////////////////////////////////////////////////////////////////

	public enum ExpandFlag {

		LATIN_ASCII,
		TRANSLITERATE,
		STRIP_ACCENTS,
		DECOMPOSE,
		LOWERCASE,
		TRIM_STRING,
		DROP_PARENTHETICALS,
		REPLACE_NUMERIC_HYPHENS,
		DELETE_NUMERIC_HYPHENS,
		SPLIT_ALPHA_FROM_NUMERIC,
		REPLACE_WORD_HYPHENS,
		DELETE_WORD_HYPHENS,
		DELETE_FINAL_PERIODS,
		DELETE_ACRONYM_PERIODS,
		DROP_ENGLISH_POSSESSIVES,
		DELETE_APOSTROPHES,
		EXPAND_NUMEX,
		ROMAN_NUMERALS;

		public static final EnumSet<ExpandFlag> DEFAULTS = EnumSet.of(
			ExpandFlag.LATIN_ASCII,
			ExpandFlag.TRANSLITERATE,
			ExpandFlag.STRIP_ACCENTS,
			ExpandFlag.DECOMPOSE,
			ExpandFlag.LOWERCASE,
			ExpandFlag.TRIM_STRING,
			ExpandFlag.DROP_PARENTHETICALS,
			ExpandFlag.SPLIT_ALPHA_FROM_NUMERIC,
			ExpandFlag.REPLACE_WORD_HYPHENS,
			ExpandFlag.DELETE_WORD_HYPHENS,
			ExpandFlag.DELETE_FINAL_PERIODS,
			ExpandFlag.DELETE_ACRONYM_PERIODS,
			ExpandFlag.DROP_ENGLISH_POSSESSIVES,
			ExpandFlag.DELETE_APOSTROPHES,
			ExpandFlag.ROMAN_NUMERALS
		);

	}

	public enum ExpandComponent {

		ANY(1),
		NAME(2),
		HOUSE_NUMBER(4),
		STREET(8),
		UNIT(16),
		LEVEL(32),
		STAIRCASE(64),
		ENTRANCE(128),
		CATEGORY(256),
		NEAR(512),
		TOPONYM(8192),
		POSTAL_CODE(16384),
		PO_BOX(32768),
		ALL(-1);

		public static final EnumSet<ExpandComponent> DEFAULTS = EnumSet.of(
			ExpandComponent.NAME,
			ExpandComponent.HOUSE_NUMBER,
			ExpandComponent.STREET,
			ExpandComponent.PO_BOX,
			ExpandComponent.UNIT,
			ExpandComponent.LEVEL,
			ExpandComponent.ENTRANCE,
			ExpandComponent.STAIRCASE,
			ExpandComponent.POSTAL_CODE);

		private final short flag;

		ExpandComponent(int flag) {
			this.flag = (short) flag;
		}

		short bits() {
			return flag;
		}

	}

	public record ExpandOptions(
		Set<ExpandComponent> components,
		Set<ExpandFlag> flags
	) {

		public static final ExpandOptions DEFAULT = new ExpandOptions(
			ExpandComponent.DEFAULTS,
			ExpandFlag.DEFAULTS);

		public static ExpandOptions of(Set<ExpandComponent> components, Set<ExpandFlag> flags) {
			return new ExpandOptions(components, flags);
		}

	}

	public String[] expandAddress(String address, String... languages) {
		return expandAddress(address, ExpandOptions.DEFAULT, languages);
	}

	public String[] expandAddress(String address, ExpandOptions options, String... languages) {
		try {
			MemorySegment size = bufferPool.allocate(JAVA_LONG, 1L);
			MemorySegment expansion = libpostal_expand_address(toCString(requireNonNull(address)), toStruct(options, languages), size);
			return toStringArray(expansion, size);
		} finally {
			bufferPool.reset();
		}
	}

	public String[] expandAddressRoot(String address, String... languages) {
		return expandAddressRoot(address, ExpandOptions.DEFAULT, languages);
	}

	public String[] expandAddressRoot(String address, ExpandOptions options, String... languages) {
		try {
			MemorySegment size = bufferPool.allocate(JAVA_LONG, 1L);
			MemorySegment expansion = libpostal_expand_address_root(toCString(requireNonNull(address)), toStruct(options, languages), size);
			return toStringArray(expansion, size);
		} finally {
			bufferPool.reset();
		}
	}

	private MemorySegment toStruct(ExpandOptions options, String... languages) {
		if (Objects.equals(options, ExpandOptions.DEFAULT) && languages.length == 0) {
			return DEFAULT_EXPAND_OPTIONS_STRUCT;
		}
		MemorySegment struct = bufferPool.allocate(libpostal_normalize_options.layout());
		libpostal_normalize_options.languages(struct, toCStringArray(languages));
		libpostal_normalize_options.num_languages(struct, languages.length);
		short components = 0;
		for (ExpandComponent component: options.components()) {
			components |= component.bits();
		}
		libpostal_normalize_options.address_components(struct, components);
		libpostal_normalize_options.latin_ascii(struct, options.flags().contains(ExpandFlag.LATIN_ASCII));
		libpostal_normalize_options.transliterate(struct, options.flags().contains(ExpandFlag.TRANSLITERATE));
		libpostal_normalize_options.strip_accents(struct, options.flags().contains(ExpandFlag.STRIP_ACCENTS));
		libpostal_normalize_options.decompose(struct, options.flags().contains(ExpandFlag.DECOMPOSE));
		libpostal_normalize_options.lowercase(struct, options.flags().contains(ExpandFlag.LOWERCASE));
		libpostal_normalize_options.trim_string(struct, options.flags().contains(ExpandFlag.TRIM_STRING));
		libpostal_normalize_options.drop_parentheticals(struct, options.flags().contains(ExpandFlag.DROP_PARENTHETICALS));
		libpostal_normalize_options.replace_numeric_hyphens(struct, options.flags().contains(ExpandFlag.REPLACE_NUMERIC_HYPHENS));
		libpostal_normalize_options.delete_numeric_hyphens(struct, options.flags().contains(ExpandFlag.DELETE_NUMERIC_HYPHENS));
		libpostal_normalize_options.replace_word_hyphens(struct, options.flags().contains(ExpandFlag.REPLACE_WORD_HYPHENS));
		libpostal_normalize_options.delete_word_hyphens(struct, options.flags().contains(ExpandFlag.DELETE_WORD_HYPHENS));
		libpostal_normalize_options.delete_final_periods(struct, options.flags().contains(ExpandFlag.DELETE_FINAL_PERIODS));
		libpostal_normalize_options.delete_acronym_periods(struct, options.flags().contains(ExpandFlag.DELETE_ACRONYM_PERIODS));
		libpostal_normalize_options.drop_english_possessives(struct, options.flags().contains(ExpandFlag.DROP_ENGLISH_POSSESSIVES));
		libpostal_normalize_options.delete_apostrophes(struct, options.flags().contains(ExpandFlag.DELETE_APOSTROPHES));
		libpostal_normalize_options.expand_numex(struct, options.flags().contains(ExpandFlag.EXPAND_NUMEX));
		libpostal_normalize_options.roman_numerals(struct, options.flags().contains(ExpandFlag.ROMAN_NUMERALS));
		return struct;
	}

	// ////////////////////////////////////////////////////////////////////////
	// Address parser
	// ////////////////////////////////////////////////////////////////////////

	public enum Label {

		HOUSE,
		HOUSE_NUMBER,
		PO_BOX,
		BUILDING,
		ENTRANCE,
		STAIRCASE,
		LEVEL,
		UNIT,
		ROAD,
		METRO_STATION,
		SUBURB,
		CITY_DISTRICT,
		CITY,
		ISLAND,
		STATE_DISTRICT,
		STATE,
		POSTCODE,
		COUNTRY_REGION,
		COUNTRY,
		WORLD_REGION,

		NEAR,
		CATEGORY,
		WEBSITE,
		TELEPHONE;

		private static final Map<String,Label> LOOKUP;
		static {
			LOOKUP = new HashMap<>();
			for (Label label: values()) {
				LOOKUP.put(label.name().toLowerCase(Locale.ENGLISH), label);
			}
		}

		public static Label fromString(String s) {
			Label label = LOOKUP.get(s);
			if (label == null) {
				throw new IllegalArgumentException("unknown label: " + s);
			}
			return label;
		}

		public static Label lookup(String s) {
			return LOOKUP.get(s);
		}

		private final String lowerName;

		Label() {
			this.lowerName = name().toLowerCase(Locale.ENGLISH);
		}

		public String toString() {
			return lowerName;
		}

	}

	public record Component(Label label, String value) {

		public Component(String label, String value) {
			this(Label.fromString(label), value);
		}

		public Component {
			requireNonNull(label);
			requireNonNull(value);
		}

		public static Component of(Label label, String value) {
			return new Component(label, value);
		}

	}

	public record Parse(List<Component> components) implements Iterable<Component> {

		public static Parse of(Component... components) {
			return new Parse(Arrays.asList(components));
		}

		public boolean has(Label label) {
			return components.stream()
				.anyMatch(c ->
					Objects.equals(c.label(), label));
		}

		public boolean has(Label first, Label... rest) {
			return has(EnumSet.of(first, rest));
		}

		public boolean has(Set<Label> labels) {
			return components.stream()
				.anyMatch(c ->
					labels.contains(c.label())
				);
		}

		public Parse select(Label label) {
			return select(EnumSet.of(label));
		}

		public Parse select(Label first, Label... rest) {
			return select(EnumSet.of(first, rest));
		}

		public Parse select(Set<Label> labels) {
			return new Parse(
				components.stream()
					.filter(c ->
						labels.contains(c.label()))
					.toList()
			);
		}

		public String toText() {
			if (components.isEmpty()) {
				return null;
			}
			return components.stream()
				.map(Component::value)
				.collect(joining(" "));
		}

		public String[] toValues() {
			return components.stream()
				.map(Component::value)
				.toArray(String[]::new);
		}

		public int size() {
			return components.size();
		}

		@Override
		public Iterator<Component> iterator() {
			return components.iterator();
		}

		public Stream<Component> stream() {
			return components.stream();
		}

	}

	public Parse parseAddress(String address) {
		try {
			// The current version (v1.1) of libpostal ignores the values in
			// libpostal_address_parser_options_t, so we don't bother with
			// supporting it in the API.
			MemorySegment response = libpostal_parse_address(toCString(requireNonNull(address)), DEFAULT_PARSE_OPTIONS_STRUCT);
			int count = Math.toIntExact(libpostal_address_parser_response.num_components(response));
			Component[] components = new Component[count];
			for (int i=0; i<count; i++) {
				components[i] = new Component(
					libpostal_address_parser_response.labels(response)
						.getAtIndex(ValueLayout.ADDRESS, i)
						.reinterpret(Long.MAX_VALUE)
						.getString(0),
					libpostal_address_parser_response.components(response)
						.getAtIndex(ValueLayout.ADDRESS, i)
						.reinterpret(Long.MAX_VALUE).getString(0));
			}
			libpostal_address_parser_response_destroy(response);
			return Parse.of(components);
		} finally {
			bufferPool.reset();
		}
	}

	// ////////////////////////////////////////////////////////////////////////
	// Language classification
	// ////////////////////////////////////////////////////////////////////////

	public record Language(String language, double confidence) {

		public Language {
			requireNonNull(language);
		}

	}

	public Language[] classifyLanguage(String address) {
		try {
			MemorySegment response = libpostal_classify_language(toCString(requireNonNull(address)));
			if (Objects.equals(response, NULL)) {
				return new Language[0];
			}
			int count = Math.toIntExact(libpostal_language_classifier_response.num_languages(response));
			Language[] languages = new Language[count];
			for (int i=0; i<count; i++) {
				languages[i] =
					new Language(
						libpostal_language_classifier_response.languages(response)
							.getAtIndex(ValueLayout.ADDRESS, i)
							.reinterpret(Long.MAX_VALUE)
							.getString(0),
						libpostal_language_classifier_response.probs(response)
							.getAtIndex(ValueLayout.JAVA_DOUBLE, i));
			}
			libpostal_language_classifier_response_destroy(response);
			return languages;
		} finally {
			bufferPool.reset();
		}
	}

	public String[] placeLanguages(Parse parse) {
		try {
			MemorySegment numLanguages = bufferPool.allocate(JAVA_LONG, 1L);
			MemorySegment labelsArray = toCStringArray(parse.stream()
				.map(Component::label)
				.map(Label::toString)
				.toArray(String[]::new));
			MemorySegment valuesArray = toCStringArray(parse.stream()
				.map(Component::value)
				.toArray(String[]::new));
			MemorySegment response = libpostal_place_languages(
				parse.size(),
				labelsArray,
				valuesArray,
				numLanguages);
			int count = Math.toIntExact(numLanguages.getAtIndex(JAVA_LONG, 0L));
			String[] languages = new String[count];
			for (int i=0; i<count; i++) {
				languages[i] = response.getAtIndex(ValueLayout.ADDRESS, i)
					.reinterpret(Long.MAX_VALUE)
					.getString(0L);
			}
			// Undocumented but the returned char** is a dynamically allocated
			// array, but its elements are owned by the language_classifier_t
			// so we just free the array of pointers, not the character arrays
			// themselves.
			free(response);
			return languages;
		} finally {
			bufferPool.reset();
		}
	}

	// ////////////////////////////////////////////////////////////////////////
	// Near-dupe hashing
	// ////////////////////////////////////////////////////////////////////////

	public enum NearDupeHashFlag {
		WITH_NAME,
		WITH_ADDRESS,
		WITH_UNIT,
		WITH_CITY_OR_EQUIVALENT,
		WITH_SMALL_CONTAINING_BOUNDARIES,
		WITH_POSTAL_CODE,
		WITH_LATLON,
		NAME_AND_ADDRESS_KEYS,
		NAME_ONLY_KEYS,
		ADDRESS_ONLY_KEYS
	}

	public record NearDupeHashOptions(Set<NearDupeHashFlag> flags, double lat, double lon, int geoPrecision) {

		public static final int DEFAULT_GEO_PRECISION = 6;

		public static final NearDupeHashOptions DEFAULT = NearDupeHashOptions.of(
			NearDupeHashFlag.WITH_NAME,
			NearDupeHashFlag.WITH_ADDRESS,
			NearDupeHashFlag.WITH_CITY_OR_EQUIVALENT,
			NearDupeHashFlag.WITH_SMALL_CONTAINING_BOUNDARIES,
			NearDupeHashFlag.WITH_POSTAL_CODE,
			NearDupeHashFlag.NAME_AND_ADDRESS_KEYS
		);

		public boolean enabled(NearDupeHashFlag flag) {
			return flags.contains(flag);
		}

		public static NearDupeHashOptions of(NearDupeHashFlag first, NearDupeHashFlag... rest) {
			return new NearDupeHashOptions(EnumSet.of(first, rest), 0d, 0d, DEFAULT_GEO_PRECISION);
		}

		public static NearDupeHashOptions of(EnumSet<NearDupeHashFlag> flags) {
			return new NearDupeHashOptions(flags, 0d, 0d, DEFAULT_GEO_PRECISION);
		}

		public NearDupeHashOptions enable(NearDupeHashFlag flag) {
			Set<NearDupeHashFlag> flags = EnumSet.copyOf(this.flags);
			if (flags.add(flag)) {
				return new NearDupeHashOptions(flags, this.lat, this.lon, this.geoPrecision);
			} else {
				return this;
			}
		}

		public NearDupeHashOptions disable(NearDupeHashFlag flag) {
			Set<NearDupeHashFlag> flags = EnumSet.copyOf(this.flags);
			if (flags.remove(flag)) {
				return new NearDupeHashOptions(flags, this.lat, this.lon, this.geoPrecision);
			} else {
				return this;
			}
		}

	}

	public String[] nearDupeNameHashes(String name, String... languages) {
		return nearDupeNameHashes(name, ExpandOptions.DEFAULT, languages);
	}

	public String[] nearDupeNameHashes(String name, ExpandOptions options, String... languages) {
		try {
			MemorySegment numHashes = bufferPool.allocate(JAVA_LONG, 1L);
			MemorySegment hashes = libpostal_near_dupe_name_hashes(toCString(requireNonNull(name)), toStruct(options, languages), numHashes);
			return toStringArray(hashes, numHashes);
		} finally {
			bufferPool.reset();
		}
	}

	public String[] nearDupeHashes(Parse parse, String... languages) {
		return nearDupeHashes(parse, NearDupeHashOptions.DEFAULT, languages);
	}

	public String[] nearDupeHashes(Parse parse, NearDupeHashOptions options, String... languages) {
		try {
			MemorySegment numHashes = bufferPool.allocate(JAVA_LONG, 1L);
			MemorySegment labelsArray = bufferPool.allocate(ValueLayout.ADDRESS, parse.size());
			MemorySegment valuesArray = bufferPool.allocate(ValueLayout.ADDRESS, parse.size());
			int index = 0; for (Component component: parse) {
				labelsArray.setAtIndex(ValueLayout.ADDRESS, index, bufferPool.allocateFrom(component.label().toString()));
				valuesArray.setAtIndex(ValueLayout.ADDRESS, index, bufferPool.allocateFrom(component.value()));
				index++;
			}
			MemorySegment languagesArray = toCStringArray(languages);
			MemorySegment hashes = libpostal_near_dupe_hashes_languages(parse.size(), labelsArray, valuesArray, toStruct(options), languages.length, languagesArray, numHashes);
			return toStringArray(hashes, numHashes);
		} finally {
			bufferPool.reset();
		}
	}

	private MemorySegment toStruct(NearDupeHashOptions options) {
		if (Objects.equals(options, NearDupeHashOptions.DEFAULT)) {
			return DEFAULT_NEAR_DUPE_OPTIONS_STRUCT;
		}
		MemorySegment struct = bufferPool.allocate(libpostal_near_dupe_hash_options.layout());
		libpostal_near_dupe_hash_options.with_name(struct, options.enabled(NearDupeHashFlag.WITH_NAME));
		libpostal_near_dupe_hash_options.with_address(struct, options.enabled(NearDupeHashFlag.WITH_ADDRESS));
		libpostal_near_dupe_hash_options.with_unit(struct, options.enabled(NearDupeHashFlag.WITH_UNIT));
		libpostal_near_dupe_hash_options.with_city_or_equivalent(struct, options.enabled(NearDupeHashFlag.WITH_CITY_OR_EQUIVALENT));
		libpostal_near_dupe_hash_options.with_small_containing_boundaries(struct, options.enabled(NearDupeHashFlag.WITH_SMALL_CONTAINING_BOUNDARIES));
		libpostal_near_dupe_hash_options.with_postal_code(struct, options.enabled(NearDupeHashFlag.WITH_POSTAL_CODE));
		libpostal_near_dupe_hash_options.with_latlon(struct, options.enabled(NearDupeHashFlag.WITH_LATLON));
		libpostal_near_dupe_hash_options.latitude(struct, options.lat());
		libpostal_near_dupe_hash_options.longitude(struct, options.lon());
		libpostal_near_dupe_hash_options.geohash_precision(struct, options.geoPrecision());
		libpostal_near_dupe_hash_options.name_and_address_keys(struct, options.enabled(NearDupeHashFlag.NAME_AND_ADDRESS_KEYS));
		libpostal_near_dupe_hash_options.name_only_keys(struct, options.enabled(NearDupeHashFlag.NAME_ONLY_KEYS));
		libpostal_near_dupe_hash_options.address_only_keys(struct, options.enabled(NearDupeHashFlag.ADDRESS_ONLY_KEYS));
		return struct;
	}

	// ////////////////////////////////////////////////////////////////////////
	// Pairwise deduping
	// ////////////////////////////////////////////////////////////////////////

	public enum DupeResult {

		MISSING,
		NO,
		POSSIBLE,
		LIKELY,
		EXACT;

		private static final Map<Integer, DupeResult> BY_STATUS = Map.of(
			LIBPOSTAL_NULL_DUPLICATE_STATUS(), MISSING,
			LIBPOSTAL_NON_DUPLICATE(), NO,
			LIBPOSTAL_POSSIBLE_DUPLICATE_NEEDS_REVIEW(), POSSIBLE,
			LIBPOSTAL_LIKELY_DUPLICATE(), LIKELY,
			LIBPOSTAL_EXACT_DUPLICATE(), EXACT
		);

		static DupeResult ofStatus(int libpostalStatus) {
			return requireNonNull(
				BY_STATUS.get(libpostalStatus),
				() ->
					"invalid libpostal_duplicate_status_t: " + libpostalStatus);
		}

	}

	public DupeResult isNameDuplicate(String lhs, String rhs, String... languages) {
		try {
			return DupeResult.ofStatus(
				libpostal_is_name_duplicate(
					toCString(lhs),
					toCString(rhs),
					toDuplicateOptions(languages)));
		} finally {
			bufferPool.reset();
		}
	}

	public DupeResult isStreetDuplicate(String lhs, String rhs, String... languages) {
		try {
			return DupeResult.ofStatus(
				libpostal_is_street_duplicate(
					toCString(lhs),
					toCString(rhs),
					toDuplicateOptions(languages)));
		} finally {
			bufferPool.reset();
		}
	}

	public DupeResult isHouseNumberDuplicate(String lhs, String rhs, String... languages) {
		try {
			return DupeResult.ofStatus(
				libpostal_is_house_number_duplicate(
					toCString(lhs),
					toCString(rhs),
					toDuplicateOptions(languages)));
		} finally {
			bufferPool.reset();
		}
	}

	public DupeResult isPOBoxDuplicate(String lhs, String rhs, String... languages) {
		try {
			return DupeResult.ofStatus(
				libpostal_is_po_box_duplicate(
					toCString(lhs),
					toCString(rhs),
					toDuplicateOptions(languages)));
		} finally {
			bufferPool.reset();
		}
	}

	public DupeResult isUnitDuplicate(String lhs, String rhs, String... languages) {
		try {
			return DupeResult.ofStatus(
				libpostal_is_unit_duplicate(
					toCString(lhs),
					toCString(rhs),
					toDuplicateOptions(languages)));
		} finally {
			bufferPool.reset();
		}
	}

	public DupeResult isFloorDuplicate(String lhs, String rhs, String... languages) {
		try {
			return DupeResult.ofStatus(
				libpostal_is_floor_duplicate(
					toCString(lhs),
					toCString(rhs),
					toDuplicateOptions(languages)));
		} finally {
			bufferPool.reset();
		}
	}

	public DupeResult isPostcodeDuplicate(String lhs, String rhs, String... languages) {
		try {
			return DupeResult.ofStatus(
				libpostal_is_postal_code_duplicate(
					toCString(lhs),
					toCString(rhs),
					toDuplicateOptions(languages)));
		} finally {
			bufferPool.reset();
		}
	}

	public DupeResult isPlaceDuplicate(Parse lhs, Parse rhs, String... languages) {
		try {
			MemorySegment lhsLabels = bufferPool.allocate(ValueLayout.ADDRESS, lhs.size());
			MemorySegment lhsValues = bufferPool.allocate(ValueLayout.ADDRESS, lhs.size());
			int lhsIndex = 0;
			for (Component lhsComponent: lhs) {
				lhsLabels.setAtIndex(ValueLayout.ADDRESS, lhsIndex, toCString(lhsComponent.label().toString()));
				lhsValues.setAtIndex(ValueLayout.ADDRESS, lhsIndex, toCString(lhsComponent.value()));
				lhsIndex++;
			}
			MemorySegment rhsLabels = bufferPool.allocate(ValueLayout.ADDRESS, rhs.size());
			MemorySegment rhsValues = bufferPool.allocate(ValueLayout.ADDRESS, rhs.size());
			int rhsIndex = 0;
			for (Component rhsComponent: rhs) {
				rhsLabels.setAtIndex(ValueLayout.ADDRESS, rhsIndex, toCString(rhsComponent.label().toString()));
				rhsValues.setAtIndex(ValueLayout.ADDRESS, rhsIndex, toCString(rhsComponent.value()));
				rhsIndex++;
			}
			return DupeResult.ofStatus(
				libpostal_is_toponym_duplicate(
					lhs.size(), lhsLabels, lhsValues,
					rhs.size(), rhsLabels, rhsValues,
					toDuplicateOptions(languages)));
		} finally {
			bufferPool.reset();
		}
	}

	private MemorySegment toDuplicateOptions(String... languages) {
		if (languages.length == 0) {
			return DEFAULT_DUPLICATE_OPTIONS_STRUCT;
		}
		MemorySegment struct = bufferPool.allocate(libpostal_duplicate_options.layout());
		libpostal_duplicate_options.languages(struct, toCStringArray(languages));
		libpostal_duplicate_options.num_languages(struct, languages.length);
		return struct;
	}

	public record FuzzyDupeOptions(double possibleThreshold, double likelyThreshold) {

		public static final FuzzyDupeOptions DEFAULT = new FuzzyDupeOptions(0.7d, 0.9d);

	}

	public record FuzzyDupe(DupeResult status, double similarity) {}

	public FuzzyDupe isNameFuzzyDuplicate(String[] lhsTokens, double[] lhsWeights, String[] rhsTokens, double[] rhsWeights, String... languages) {
		return isNameFuzzyDuplicate(lhsTokens, lhsWeights, rhsTokens, rhsWeights, FuzzyDupeOptions.DEFAULT, languages);
	}

	public FuzzyDupe isNameFuzzyDuplicate(String[] lhsTokens, double[] lhsWeights, String[] rhsTokens, double[] rhsWeights, FuzzyDupeOptions options, String... languages) {
		try {
			MemorySegment response =
				libpostal_is_name_duplicate_fuzzy(
					bufferPool,
					lhsTokens.length, toCStringArray(lhsTokens), toCDoubleArray(lhsWeights),
					rhsTokens.length, toCStringArray(rhsTokens), toCDoubleArray(rhsWeights),
					toStruct(options, languages));
			return new FuzzyDupe(
				DupeResult.ofStatus(libpostal_fuzzy_duplicate_status.status(response)),
				libpostal_fuzzy_duplicate_status.similarity(response));
		} finally {
			bufferPool.reset();
		}
	}

	public FuzzyDupe isStreetFuzzyDuplicate(String[] lhsTokens, double[] lhsWeights, String[] rhsTokens, double[] rhsWeights, String... languages) {
		return isStreetFuzzyDuplicate(lhsTokens, lhsWeights, rhsTokens, rhsWeights, FuzzyDupeOptions.DEFAULT, languages);
	}

	public FuzzyDupe isStreetFuzzyDuplicate(String[] lhsTokens, double[] lhsWeights, String[] rhsTokens, double[] rhsWeights, FuzzyDupeOptions options, String... languages) {
		try {
			MemorySegment response =
				libpostal_is_street_duplicate_fuzzy(
					bufferPool,
					lhsTokens.length, toCStringArray(lhsTokens), toCDoubleArray(lhsWeights),
					rhsTokens.length, toCStringArray(rhsTokens), toCDoubleArray(rhsWeights),
					toStruct(options, languages));
			return new FuzzyDupe(
				DupeResult.ofStatus(libpostal_fuzzy_duplicate_status.status(response)),
				libpostal_fuzzy_duplicate_status.similarity(response));
		} finally {
			bufferPool.reset();
		}
	}

	private MemorySegment toStruct(FuzzyDupeOptions options, String... languages) {
		if (Objects.equals(options, FuzzyDupeOptions.DEFAULT) && languages.length == 0) {
			return DEFAULT_FUZZY_DUPLICATE_OPTIONS_STRUCT;
		}
		MemorySegment struct = bufferPool.allocate(libpostal_fuzzy_duplicate_options.layout());
		libpostal_fuzzy_duplicate_options.languages(struct, toCStringArray(languages));
		libpostal_fuzzy_duplicate_options.num_languages(struct, languages.length);
		libpostal_fuzzy_duplicate_options.needs_review_threshold(struct, options.possibleThreshold());
		libpostal_fuzzy_duplicate_options.likely_dupe_threshold(struct, options.likelyThreshold());
		return struct;
	}

	// ////////////////////////////////////////////////////////////////////////
	// Tokenization
	// ////////////////////////////////////////////////////////////////////////

	public enum NormalizeFlag {

		LATIN_ASCII(1L),
		TRANSLITERATE(2L),
		STRIP_ACCENTS(4L),
		DECOMPOSE(8L),
		LOWERCASE(16L),
		TRIM(32L),
		REPLACE_HYPHENS(64L),
		COMPOSE(128L),
		SIMPLE_LATIN_ASCII(256L),
		REPLACE_NUMEX(512L);

		public static final Set<NormalizeFlag> DEFAULTS = EnumSet.of(
			LATIN_ASCII,
			STRIP_ACCENTS,
			LOWERCASE,
			TRIM,
			REPLACE_HYPHENS,
			COMPOSE);

		private final long bits;

		NormalizeFlag(long bits) {
			this.bits = bits;
		}

		public long bits() {
			return bits;
		}

	}

	public enum TokenFlag {

		REPLACE_HYPHENS(1L),
		DELETE_HYPHENS(2L),
		DELETE_FINAL_PERIOD(4L),
		DELETE_ACRONYM_PERIODS(8L),
		DROP_ENGLISH_POSSESSIVES(16L),
		DELETE_OTHER_APOSTROPHES(32L),
		SPLIT_ALPHA_FROM_NUMERIC(64L),
		REPLACE_DIGITS(128L),
		REPLACE_NUMERIC_TOKEN_LETTERS(256L),
		REPLACE_NUMERIC_HYPHENS(512L);

		public static final Set<TokenFlag> DEFAULTS = EnumSet.of(
			REPLACE_HYPHENS,
			DELETE_FINAL_PERIOD,
			DELETE_ACRONYM_PERIODS,
			DROP_ENGLISH_POSSESSIVES,
			DELETE_OTHER_APOSTROPHES);

		public static final Set<TokenFlag> DROP_PERIODS = EnumSet.of(
			DELETE_FINAL_PERIOD,
			DELETE_ACRONYM_PERIODS);

		public static final Set<TokenFlag> NUMERIC = EnumSet.of(
			REPLACE_HYPHENS,
			DELETE_FINAL_PERIOD,
			DELETE_ACRONYM_PERIODS,
			DROP_ENGLISH_POSSESSIVES,
			DELETE_OTHER_APOSTROPHES,
			SPLIT_ALPHA_FROM_NUMERIC);

		private final long bits;

		TokenFlag(long bits) {
			this.bits = bits;
		}

		public long bits() {
			return bits;
		}

	}

	public record Token(String chars, Type type, long offset, long length) {

		public enum Type {
			END(0),

			WORD(1),
			ABBREVIATION(2),
			IDEOGRAPHIC_CHAR(3),
			HANGUL_SYLLABLE(4),
			ACRONYM(5),

			PHRASE(10),

			EMAIL(20),
			URL(21),
			US_PHONE(22),
			INTL_PHONE(23),

			NUMERIC(50),
			ORDINAL(51),
			ROMAN_NUMERAL(52),
			IDEOGRAPHIC_NUMBER(53),

			PERIOD(100),
			EXCLAMATION(101),
			QUESTION_MARK(102),
			COMMA(103),
			COLON(104),
			SEMICOLON(105),
			PLUS(106),
			AMPERSAND(107),
			AT_SIGN(108),
			POUND(109),
			ELLIPSIS(110),
			DASH(111),
			BREAKING_DASH(112),
			HYPHEN(113),
			PUNCT_OPEN(114),
			PUNCT_CLOSE(115),
			DOUBLE_QUOTE(119),
			SINGLE_QUOTE(120),
			SLASH(124),
			BACKSLASH(125),
			GREATER_THAN(126),
			LESS_THAN(127),

			OTHER(200),
			WHITESPACE(300),
			NEWLINE(301),

			INVALID_CHAR(500);

			private final int libpostalType;

			Type(int libpostalType) {
				this.libpostalType = libpostalType;
			}

			private static final Map<Integer,Type> FROM_LIBPOSTAL;
			static {
				FROM_LIBPOSTAL = new HashMap<>();
				for (Type value: values()) {
					FROM_LIBPOSTAL.put(value.libpostalType, value);
				}
			}

			static Type fromLibpostal(int libpostalType) {
				return requireNonNull(
					FROM_LIBPOSTAL.get(libpostalType),
					() ->
						"invalid libpostal_token_type_t: " + libpostalType);
			}

		}

	}

	private static final MemoryLayout ARRAY_OF_TOKEN_T = MemoryLayout.sequenceLayout(Long.MAX_VALUE/libpostal_normalized_token.sizeof(), libpostal_normalized_token.layout());
	private static final MethodHandle TOKEN_AT = ARRAY_OF_TOKEN_T.sliceHandle(MemoryLayout.PathElement.sequenceElement());

	public Token[] tokenize(String text, String... languages) {
		return tokenize(text, NormalizeFlag.DEFAULTS, TokenFlag.DEFAULTS, languages);
	}

	public Token[] tokenize(String text, Set<NormalizeFlag> normalizeFlags, Set<TokenFlag> tokenFlags, String... languages) {
		try {
			MemorySegment numTokens = bufferPool.allocate(JAVA_LONG);
			long normalizeBits = 0L;
			for (NormalizeFlag flag : normalizeFlags) {
				normalizeBits |= flag.bits();
			}
			long tokenBits = 0L;
			for (TokenFlag flag : tokenFlags) {
				tokenBits |= flag.bits();
			}
			MemorySegment response = libpostal_normalized_tokens_languages(
				toCString(requireNonNull(text)),
				normalizeBits,
				tokenBits,
				false,
				languages.length,
				toCStringArray(languages),
				numTokens);
			Token[] tokens = new Token[Math.toIntExact(numTokens.get(JAVA_LONG, 0L))];
			for (int i = 0; i<tokens.length; i++) {
				MemorySegment slice = (MemorySegment) TOKEN_AT.invokeExact(response, 0L, (long) i);
				MemorySegment str = libpostal_normalized_token.str(slice).reinterpret(Long.MAX_VALUE);
				MemorySegment tokenSlice = libpostal_normalized_token.token(slice);
				tokens[i] = new Token(
					str.getString(0L),
					Token.Type.fromLibpostal(libpostal_token.type(tokenSlice)),
					libpostal_token.offset(tokenSlice),
					libpostal_token.len(tokenSlice));
				free(str);
			}
			free(response);
			return tokens;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		} finally {
			bufferPool.reset();
		}
	}

	// ////////////////////////////////////////////////////////////////////////
	// FFI utilities
	// ////////////////////////////////////////////////////////////////////////

	private MemorySegment toCString(String s) {
		return s != null
			? bufferPool.allocateFrom(s)
			: NULL;
	}

	private String[] toStringArray(MemorySegment array, MemorySegment arrayLength) {
		int count = Math.toIntExact(arrayLength.get(JAVA_LONG, 0L));
		String[] strings = new String[count];
		for (int i=0; i<count; i++) {
			strings[i] = array.getAtIndex(ValueLayout.ADDRESS, i).reinterpret(Long.MAX_VALUE).getString(0);
		}
		// Despite its name, this can be used to free dedupe hashes as well
		libpostal_expansion_array_destroy(array, count);
		return strings;
	}

	private MemorySegment toCDoubleArray(double[] doubles) {
		MemorySegment array = NULL;
		if (doubles.length > 0) {
			array = bufferPool.allocate(JAVA_DOUBLE, doubles.length);
			for (int i=0; i<doubles.length; i++) {
				array.setAtIndex(JAVA_DOUBLE, i, doubles[i]);
			}
		}
		return array;
	}

	private MemorySegment toCStringArray(String[] strings) {
		MemorySegment array = NULL;
		if (strings.length > 0) {
			array = bufferPool.allocate(ValueLayout.ADDRESS, strings.length);
			for (int i=0; i<strings.length; i++) {
				array.setAtIndex(ValueLayout.ADDRESS, i, bufferPool.allocateFrom(strings[i]));
			}
		}
		return array;
	}

}
