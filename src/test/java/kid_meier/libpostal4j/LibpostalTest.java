package kid_meier.libpostal4j;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import kid_meier.libpostal4j.Libpostal.Component;
import kid_meier.libpostal4j.Libpostal.Label;
import kid_meier.libpostal4j.Libpostal.Language;
import kid_meier.libpostal4j.Libpostal.ExpandComponent;
import kid_meier.libpostal4j.Libpostal.ExpandFlag;
import kid_meier.libpostal4j.Libpostal.ExpandOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static java.util.Collections.emptyList;
import static java.util.EnumSet.allOf;
import static java.util.EnumSet.noneOf;
import static kid_meier.libpostal4j.Libpostal.DupeResult.EXACT;
import static kid_meier.libpostal4j.Libpostal.DupeResult.LIKELY;
import static kid_meier.libpostal4j.Libpostal.DupeResult.MISSING;
import static kid_meier.libpostal4j.Libpostal.DupeResult.POSSIBLE;
import static org.junit.jupiter.api.Assertions.*;

class LibpostalTest {

	private static Libpostal fixture;

	@BeforeAll
	static void initialize() {
		fixture = Libpostal.initialize();
	}

	@AfterAll
	static void teardown() {
		fixture.close();
		fixture = null;
	}

	@Test
	void expandAddress() {
		List<String> addresses = Arrays.asList(fixture.expandAddress("230 Park Ave, Floor 3, New York, NY 10169 USA"));
		assertEquals(
			List.of(
				"230 park avenue floor 3 new york ny 10169 usa",
				"230 park avenue floor 3 new york new york 10169 usa"),
			addresses);
	}

	@Test
	void expandAddressOptions() {
		ExpandOptions options = ExpandOptions.of(
			noneOf(ExpandComponent.class),
			allOf(ExpandFlag.class)
		);
		List<String> addresses = Arrays.asList(fixture.expandAddress("230 Park Ave, Floor 3, New York, NY 10169 USA", options));
		assertEquals(
			List.of("230 park ave floor 3 new york ny 10169 usa"),
			addresses);
	}

	@Test
	void expandRoot() {
		List<String> roots = Arrays.asList(fixture.expandAddressRoot("230 Park Ave, Floor 3, New York, NY 10169 USA"));
		assertEquals(
			List.of("230 3 new york ny 10169 usa",
				"230 3 new york new york 10169 usa"),
			roots);
	}

	@Test
	void expandRootOptions() {
		ExpandOptions options = ExpandOptions.of(
			EnumSet.of(ExpandComponent.LEVEL, ExpandComponent.TOPONYM),
			allOf(ExpandFlag.class)
		);
		List<String> roots = Arrays.asList(fixture.expandAddressRoot("230 Park Ave, Floor 3, New York, NY 10169 USA", options, "en"));
		assertEquals(
			List.of("230 park ave 3 new york ny 10169 usa",
				"230 park ave 3 new york new york 10169 usa"),
			roots);
	}

	@Test
	void parseAddress() {
		Libpostal.Parse parse = fixture.parseAddress("230 Park Ave, Floor 3, New York, NY 10169 USA");
		assertEquals(
			Libpostal.Parse.of(
				Component.of(Label.HOUSE_NUMBER, "230"),
				Component.of(Label.ROAD, "park ave"),
				Component.of(Label.LEVEL, "floor 3"),
				Component.of(Label.CITY, "new york"),
				Component.of(Label.STATE, "ny"),
				Component.of(Label.POSTCODE, "10169"),
				Component.of(Label.COUNTRY, "usa")),
			parse);
	}

	@Test
	void classifyLanguage() {
		List<Language> languages = Arrays.asList(fixture.classifyLanguage("230 Park Ave, Floor 3, New York, NY 10169 USA"));
		assertEquals(List.of(new Language("en", 0.9999994097459571)), languages);
	}

	@Test
	void classifyAmbiguousLanguage() {
		List<Language> languages = Arrays.asList(fixture.classifyLanguage("10169"));
		assertEquals(emptyList(), languages);
	}

	@Test
	void placeLanguages() {
		List<String> languages = Arrays.asList(fixture.placeLanguages(
			Libpostal.Parse.of(
				Component.of(Label.HOUSE_NUMBER, "230"),
				Component.of(Label.ROAD, "park ave"),
				Component.of(Label.LEVEL, "floor 3"),
				Component.of(Label.CITY, "new york"),
				Component.of(Label.STATE, "ny"),
				Component.of(Label.POSTCODE, "10169"),
				Component.of(Label.COUNTRY, "usa"))));
		assertEquals(List.of("en"), languages);
	}

	@Test
	void nearDupeNameHashes() {
		List<String> hashes = Arrays.asList(
			fixture.nearDupeNameHashes("John E Amos DDS", "en"));
		assertEquals(
			List.of(
				"JN", "AN", "john",
				"AST", "east",
				"AMS", "amos",
				"TS", "dds",
				"JNST", "NSTM", "STMS", "TMST", "MSTS", "ANST",
				"A", "e",
				"JNMS", "NMST", "ANMS", "JT", "AT"),
			hashes);
	}

	@Test
	void nearDupeHashes() {
		List<String> hashes = Arrays.asList(
			fixture.nearDupeHashes(
				Libpostal.Parse.of(
					Component.of(Label.HOUSE_NUMBER, "230"),
					Component.of(Label.ROAD, "park ave"),
					Component.of(Label.LEVEL, "floor 3"),
					Component.of(Label.CITY, "new york"),
					Component.of(Label.STATE, "ny"),
					Component.of(Label.POSTCODE, "10169"),
					Component.of(Label.COUNTRY, "usa")),
				Libpostal.NearDupeHashOptions.DEFAULT
					.enable(Libpostal.NearDupeHashFlag.ADDRESS_ONLY_KEYS)
					.disable(Libpostal.NearDupeHashFlag.NAME_AND_ADDRESS_KEYS)
					.disable(Libpostal.NearDupeHashFlag.WITH_NAME)
			));
		assertEquals(
			List.of(
				"act|park avenue|230|new york",
				"act|park|230|new york",
				"apc|park avenue|230|10169",
				"apc|park|230|10169"),
			hashes);
	}

	@Test
	void nearDupeHashesOptionsLanguages() {
		List<String> searches = Arrays.asList(
			fixture.nearDupeHashes(
				Libpostal.Parse.of(
					Component.of(Label.HOUSE_NUMBER, "230"),
					Component.of(Label.ROAD, "park ave"),
					Component.of(Label.LEVEL, "floor 3"),
					Component.of(Label.CITY, "new york"),
					Component.of(Label.STATE, "ny"),
					Component.of(Label.POSTCODE, "10169"),
					Component.of(Label.COUNTRY, "usa")),
				Libpostal.NearDupeHashOptions.of(EnumSet.allOf(Libpostal.NearDupeHashFlag.class)),
				"en"));
		assertEquals(
			List.of(
				"auct|park avenue|230|3|new york",
				"auct|park|230|3|new york",
				"aupc|park avenue|230|3|10169",
				"aupc|park|230|3|10169"),
			searches);
	}


	@Test
	void isDuplicate() {
		Libpostal.Parse lhs = fixture.parseAddress("450 Sutter St San Francisco CA 94108");
		Libpostal.Parse rhs = fixture.parseAddress("450 Sutter Street Rm 1418 San Francisco CA 94108");
		assertEquals(
			MISSING,
			fixture.isNameDuplicate(
				lhs.select(Label.HOUSE).toText(),
				rhs.select(Label.HOUSE).toText()));
		assertEquals(
			EXACT,
			fixture.isStreetDuplicate(
				lhs.select(Label.ROAD).toText(),
				rhs.select(Label.ROAD).toText()));
		assertEquals(
			EXACT,
			fixture.isHouseNumberDuplicate(
				lhs.select(Label.HOUSE_NUMBER).toText(),
				rhs.select(Label.HOUSE_NUMBER).toText()));
		assertEquals(
			MISSING,
			fixture.isPOBoxDuplicate(
				lhs.select(Label.PO_BOX).toText(),
				rhs.select(Label.PO_BOX).toText()));
		assertEquals(
			MISSING,
			fixture.isUnitDuplicate(
				lhs.select(Label.UNIT).toText(),
				rhs.select(Label.UNIT).toText()));
		assertEquals(
			MISSING,
			fixture.isFloorDuplicate(
				lhs.select(Label.LEVEL).toText(),
				rhs.select(Label.LEVEL).toText()));
		assertEquals(
			EXACT,
			fixture.isPostcodeDuplicate(
				lhs.select(Label.POSTCODE).toText(),
				rhs.select(Label.POSTCODE).toText()));
		assertEquals(
			EXACT,
			fixture.isPlaceDuplicate(lhs, rhs));
	}

	@Test
	void isFuzzyDuplicate() {
		Libpostal.Parse lhs = fixture.parseAddress("John E Amos DDS, 450 Sutter St San Francisco CA 94108");
		Libpostal.Parse rhs = fixture.parseAddress("Amos John E DDS, 450 Sutter Street Rm 1418 San Francisco CA 94108");
		String lhsName = lhs.select(Label.HOUSE).toText();
		String rhsName = rhs.select(Label.HOUSE).toText();
		String[] lhsNameTokens = Arrays.stream(fixture.tokenize(lhsName))
			.map(Libpostal.Token::chars)
			.toArray(String[]::new);
		String[] rhsNameTokens = Arrays.stream(fixture.tokenize(rhsName))
			.map(Libpostal.Token::chars)
			.toArray(String[]::new);
		double[] lhsNameWeights = new double[lhsNameTokens.length];
		double[] rhsNameWeights = new double[rhsNameTokens.length];
		Arrays.fill(lhsNameWeights, 1d/lhsNameWeights.length);
		Arrays.fill(rhsNameWeights, 1d/rhsNameWeights.length);

		String lhsStreet = lhs.select(Label.HOUSE_NUMBER, Label.ROAD, Label.LEVEL, Label.UNIT, Label.STAIRCASE, Label.ENTRANCE).toText();
		String rhsStreet = rhs.select(Label.HOUSE_NUMBER, Label.ROAD, Label.LEVEL, Label.UNIT, Label.STAIRCASE, Label.ENTRANCE).toText();
		String[] lhsStreetTokens = Arrays.stream(fixture.tokenize(lhsStreet))
			.map(Libpostal.Token::chars)
			.toArray(String[]::new);
		String[] rhsStreetTokens = Arrays.stream(fixture.tokenize(rhsStreet))
			.map(Libpostal.Token::chars)
			.toArray(String[]::new);
		double[] lhsStreetWeights = new double[lhsStreetTokens.length];
		double[] rhsStreetWeights = new double[rhsStreetTokens.length];
		Arrays.fill(lhsStreetWeights, 1d/lhsStreetWeights.length);
		Arrays.fill(rhsStreetWeights, 1d/rhsStreetWeights.length);

		Libpostal.FuzzyDupe nameDupe = fixture.isNameFuzzyDuplicate(
			lhsNameTokens, lhsNameWeights,
			rhsNameTokens, rhsNameWeights,
			"en");
		Libpostal.FuzzyDupe streetDupe = fixture.isStreetFuzzyDuplicate(
			lhsStreetTokens, lhsStreetWeights,
			rhsStreetTokens, rhsStreetWeights,
			"en");

		assertEquals(new Libpostal.FuzzyDupe(LIKELY, 1d), nameDupe);
		assertEquals(new Libpostal.FuzzyDupe(LIKELY, 0.7745966692414833d), streetDupe);
	}

	@Test
	void isFuzzyDuplicate_MidtownToronto() {
		String lhsName = "Toronto";
		String rhsName = "Midtown Toronto";
		String[] lhsNameTokens = Arrays.stream(fixture.tokenize(lhsName))
			.map(Libpostal.Token::chars)
			.toArray(String[]::new);
		String[] rhsNameTokens = Arrays.stream(fixture.tokenize(rhsName))
			.map(Libpostal.Token::chars)
			.toArray(String[]::new);
		double[] lhsNameWeights = new double[lhsNameTokens.length];
		double[] rhsNameWeights = new double[rhsNameTokens.length];
		Arrays.fill(lhsNameWeights, 1d/lhsNameWeights.length);
		Arrays.fill(rhsNameWeights, 1d/rhsNameWeights.length);
		Libpostal.DupeResult nameDupe = fixture.isNameDuplicate(lhsName, rhsName);
		Libpostal.FuzzyDupe fuzzyDupe = fixture.isNameFuzzyDuplicate(
			lhsNameTokens, lhsNameWeights,
			rhsNameTokens, rhsNameWeights,
			"en");
		assertEquals(Libpostal.DupeResult.NO, nameDupe);
		assertEquals(new Libpostal.FuzzyDupe(POSSIBLE, 0.7071067811865475d), fuzzyDupe);
	}

	@Test
	void tokenize() {
		List<Libpostal.Token> tokens = Arrays.asList(fixture.tokenize(
			"230 Park Ave, Floor 3, New York, NY 10169 USA",
			Libpostal.NormalizeFlag.DEFAULTS,
			Libpostal.TokenFlag.DEFAULTS));
		assertEquals(
			List.of(
				new Libpostal.Token("230", Libpostal.Token.Type.NUMERIC, 0, 3),
				new Libpostal.Token("park", Libpostal.Token.Type.WORD, 4, 4),
				new Libpostal.Token("ave", Libpostal.Token.Type.WORD, 9, 3),
				new Libpostal.Token(",", Libpostal.Token.Type.COMMA, 12, 1),
				new Libpostal.Token("floor", Libpostal.Token.Type.WORD, 14, 5),
				new Libpostal.Token("3", Libpostal.Token.Type.NUMERIC, 20, 1),
				new Libpostal.Token(",", Libpostal.Token.Type.COMMA, 21, 1),
				new Libpostal.Token("new", Libpostal.Token.Type.WORD, 23, 3),
				new Libpostal.Token("york", Libpostal.Token.Type.WORD, 27, 4),
				new Libpostal.Token(",", Libpostal.Token.Type.COMMA, 31, 1),
				new Libpostal.Token("ny", Libpostal.Token.Type.WORD, 33, 2),
				new Libpostal.Token("10169", Libpostal.Token.Type.NUMERIC, 36, 5),
				new Libpostal.Token("usa", Libpostal.Token.Type.WORD, 42, 3)),
			tokens);
	}

}