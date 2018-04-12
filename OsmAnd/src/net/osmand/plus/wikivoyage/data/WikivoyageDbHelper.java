package net.osmand.plus.wikivoyage.data;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import net.osmand.Collator;
import net.osmand.CollatorStringMatcher;
import net.osmand.CollatorStringMatcher.StringMatcherMode;
import net.osmand.IndexConstants;
import net.osmand.OsmAndCollator;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.api.SQLiteAPI.SQLiteConnection;
import net.osmand.plus.api.SQLiteAPI.SQLiteCursor;
import net.osmand.util.Algorithms;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import gnu.trove.map.hash.TLongObjectHashMap;

public class WikivoyageDbHelper {

	private static final String ARTICLES_TABLE_NAME = "wikivoyage_articles";
	private static final String ARTICLES_COL_ID = "article_id";
	private static final String ARTICLES_COL_TITLE = "title";
	private static final String ARTICLES_COL_CONTENT = "content_gz";
	private static final String ARTICLES_COL_IS_PART_OF = "is_part_of";
	private static final String ARTICLES_COL_LAT = "lat";
	private static final String ARTICLES_COL_LON = "lon";
	private static final String ARTICLES_COL_IMAGE_TITLE = "image_title";
	private static final String ARTICLES_COL_GPX_GZ = "gpx_gz";
	private static final String ARTICLES_COL_CITY_ID = "city_id";
	private static final String ARTICLES_COL_ORIGINAL_ID = "original_id";
	private static final String ARTICLES_COL_LANG = "lang";
	private static final String ARTICLES_COL_CONTENTS_JSON = "contents_json";
	private static final String ARTICLES_COL_AGGREGATED_PART_OF = "aggregated_part_of";

	private static final String ARTICLES_TABLE_SELECT = "SELECT " +
			ARTICLES_COL_ID + ", " +
			ARTICLES_COL_TITLE + ", " +
			ARTICLES_COL_CONTENT + ", " +
			ARTICLES_COL_IS_PART_OF + ", " +
			ARTICLES_COL_LAT + ", " +
			ARTICLES_COL_LON + ", " +
			ARTICLES_COL_IMAGE_TITLE + ", " +
			ARTICLES_COL_GPX_GZ + ", " +
			ARTICLES_COL_CITY_ID + ", " +
			ARTICLES_COL_ORIGINAL_ID + ", " +
			ARTICLES_COL_LANG + ", " +
			ARTICLES_COL_CONTENTS_JSON + ", " +
			ARTICLES_COL_AGGREGATED_PART_OF +
			" FROM " + ARTICLES_TABLE_NAME;

	private static final String SEARCH_TABLE_NAME = "wikivoyage_search";
	private static final String SEARCH_COL_SEARCH_TERM = "search_term";
	private static final String SEARCH_COL_CITY_ID = "city_id";
	private static final String SEARCH_COL_ARTICLE_TITLE = "article_title";
	private static final String SEARCH_COL_LANG = "lang";

	private final OsmandApplication application;
	private SQLiteConnection connection = null;
	private File selectedTravelBook = null;
	private List<File> existingTravelBooks = new ArrayList<>();
	private Collator collator;

	public WikivoyageDbHelper(OsmandApplication application) {
		this.application = application;
		collator = OsmAndCollator.primaryCollator();
		initTravelBooks();
	}

	public void initTravelBooks() {
		File[] possibleFiles = application.getAppPath(IndexConstants.WIKIVOYAGE_INDEX_DIR).listFiles();
		String travelBook = application.getSettings().SELECTED_TRAVEL_BOOK.get();
		if (possibleFiles != null) {
			for (File f : possibleFiles) {
				if (f.getName().endsWith(IndexConstants.BINARY_WIKIVOYAGE_MAP_INDEX_EXT)) {
					existingTravelBooks.add(f);
					if (selectedTravelBook == null) {
						selectedTravelBook = f;
					} else if (Algorithms.objectEquals(travelBook, f.getName())) {
						selectedTravelBook = f;
					}
				}
			}
		}
	}

	public File getSelectedTravelBook() {
		return selectedTravelBook;
	}

	public List<File> getExistingTravelBooks() {
		return existingTravelBooks;
	}

	@Nullable
	private SQLiteConnection openConnection() {
		if (connection == null && selectedTravelBook != null) {
			application.getSettings().SELECTED_TRAVEL_BOOK.set(selectedTravelBook.getName());
			connection = application.getSQLiteAPI().openByAbsolutePath(selectedTravelBook.getAbsolutePath(), true);
		}
		return connection;
	}

	public void closeConnection() {
		if (connection != null) {
			connection.close();
			connection = null;
		}
	}

	@NonNull
	public List<WikivoyageSearchResult> search(final String searchQuery) {
		List<WikivoyageSearchResult> res = new ArrayList<>();
		SQLiteConnection conn = openConnection();
		String[] queries = searchQuery.replace('_', ' ').replace('/', ' ').split(" ");
		if (conn != null) {
			List<String> params = new ArrayList<>();
			String query = "SELECT  distinct wa.city_id, wa.title, wa.lang, wa.is_part_of, wa.image_title "
					+ "FROM wikivoyage_articles wa WHERE wa.city_id in "
					+ " (SELECT city_id FROM wikivoyage_search WHERE search_term LIKE";
			for (String q : queries) {
				if (q.trim().length() > 0) {
					if (params.size() > 5) {
						// don't explode the query search much
						break;
					}
					if (params.size() > 0) {
						query += " AND city_id IN (SELECT city_id FROM wikivoyage_search WHERE search_term LIKE ?) ";
					} else {
						query += "?";
					}
					params.add(q.trim() + "%");
				}
			}
			query += ") ";
			if (params.size() > 0) {
				SQLiteCursor cursor = conn.rawQuery(query, params.toArray(new String[params.size()]));
				if (cursor.moveToFirst()) {
					do {
						WikivoyageSearchResult rs = new WikivoyageSearchResult();
						rs.cityId = cursor.getLong(0);
						rs.articleTitles.add(cursor.getString(1));
						rs.langs.add(cursor.getString(2));
						rs.isPartOf = cursor.getString(3);
						rs.imageTitle = cursor.getString(4);
						res.add(rs);
					} while (cursor.moveToNext());
				}
				cursor.close();
			}
		}

		List<WikivoyageSearchResult> list = new ArrayList<>(groupSearchResultsByCityId(res));
		sortSearchResults(searchQuery, list);

		return list;
	}

	private void sortSearchResults(final String searchQuery, List<WikivoyageSearchResult> list) {
		Collections.sort(list, new Comparator<WikivoyageSearchResult>() {
			@Override
			public int compare(WikivoyageSearchResult o1, WikivoyageSearchResult o2) {
				boolean c1 = CollatorStringMatcher.cmatches(collator, searchQuery, o1.articleTitles.get(0),
						StringMatcherMode.CHECK_ONLY_STARTS_WITH);
				boolean c2 = CollatorStringMatcher.cmatches(collator, searchQuery, o2.articleTitles.get(0),
						StringMatcherMode.CHECK_ONLY_STARTS_WITH);
				if (c1 == c2) {
					return collator.compare(o1.articleTitles.get(0), o2.articleTitles.get(0));
				} else if (c1) {
					return -1;
				} else if (c2) {
					return 1;
				}
				return 0;
			}
		});
	}

	private Collection<WikivoyageSearchResult> groupSearchResultsByCityId(List<WikivoyageSearchResult> res) {
		String baseLng = application.getLanguage();
		TLongObjectHashMap<WikivoyageSearchResult> wikivoyage = new TLongObjectHashMap<>();
		for (WikivoyageSearchResult rs : res) {
			WikivoyageSearchResult prev = wikivoyage.get(rs.cityId);
			if (prev != null) {
				int insInd = prev.langs.size();
				if (rs.langs.get(0).equals(baseLng)) {
					insInd = 0;
				} else if (rs.langs.get(0).equals("en")) {
					if (!prev.langs.get(0).equals(baseLng)) {
						insInd = 0;
					} else {
						insInd = 1;
					}
				}
				prev.articleTitles.add(insInd, rs.articleTitles.get(0));
				prev.langs.add(insInd, rs.langs.get(0));
			} else {
				wikivoyage.put(rs.cityId, rs);
			}
		}
		return wikivoyage.valueCollection();
	}

	@Nullable
	public WikivoyageArticle getArticle(long cityId, String lang) {
		WikivoyageArticle res = null;
		SQLiteConnection conn = openConnection();
		if (conn != null) {
			SQLiteCursor cursor = conn.rawQuery(ARTICLES_TABLE_SELECT + " WHERE " + ARTICLES_COL_CITY_ID + " = ? AND "
					+ ARTICLES_COL_LANG + " = ?", new String[]{String.valueOf(cityId), lang});
			if (cursor.moveToFirst()) {
				res = readArticle(cursor);
			}
			cursor.close();
		}
		return res;
	}

	@NonNull
	public ArrayList<String> getArticleLangs(long cityId) {
		ArrayList<String> res = new ArrayList<>();
		SQLiteConnection conn = openConnection();
		if (conn != null) {
			SQLiteCursor cursor = conn.rawQuery("SELECT " + ARTICLES_COL_LANG + " FROM " + ARTICLES_TABLE_NAME
					+ " WHERE " + ARTICLES_COL_CITY_ID + " = ?", new String[]{String.valueOf(cityId)});
			if (cursor.moveToFirst()) {
				String baseLang = application.getLanguage();
				do {
					String lang = cursor.getString(0);
					if (lang.equals(baseLang)) {
						res.add(0, lang);
					} else if (lang.equals("en")) {
						if (res.size() > 0 && res.get(0).equals(baseLang)) {
							res.add(1, lang);
						} else {
							res.add(0, lang);
						}
					} else {
						res.add(lang);
					}
				} while (cursor.moveToNext());
			}
			cursor.close();

		}
		return res;
	}

	@NonNull
	private WikivoyageArticle readArticle(SQLiteCursor cursor) {
		WikivoyageArticle res = new WikivoyageArticle();

		res.id = cursor.getString(0);
		res.title = cursor.getString(1);
		try {
			res.content = Algorithms.gzipToString(cursor.getBlob(2));
		} catch (IOException e) {
			e.printStackTrace();
		}
		res.isPartOf = cursor.getString(3);
		res.lat = cursor.getDouble(4);
		res.lon = cursor.getDouble(5);
		res.imageTitle = cursor.getString(6);
		byte[] gpxFileBlob = cursor.getBlob(7);
		res.cityId = cursor.getLong(8);
		res.originalId = cursor.getLong(9);
		res.lang = cursor.getString(10);
		res.contentsJson = cursor.getString(11);
		res.aggregatedPartOf = cursor.getString(12);

		return res;
	}
}
