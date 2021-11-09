package ac.robinson.animaltagscanner;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.core.content.FileProvider;

@SuppressWarnings("deprecation")
public class SelectFileActivity extends ListActivity {

	private static final String ITEM_KEY = "key";
	public static final String START_PATH = "start_path";
	public static final String HIGHLIGHTED_FILE = "highlighted_file";
	private static final SimpleDateFormat FILE_DISPLAY_FORMAT = new SimpleDateFormat("EEEE, MMMM d, yyyy, h:mm:ss a", Locale.US);

	private HorizontalScrollView mPathViewHolder;
	private TextView mPathView;
	private final ArrayList<HashMap<String, String>> mFileList = new ArrayList<>();
	private final List<String> mPaths = new ArrayList<>();
	private String mHighlightedFile;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setResult(Activity.RESULT_CANCELED, getIntent());

		setContentView(R.layout.select_file_main);
		mPathViewHolder = findViewById(R.id.path_bar);
		mPathView = findViewById(R.id.path);
		findViewById(R.id.path_bar_icon).setOnClickListener(v -> finish());

		String highlightedFile = getIntent().getStringExtra(HIGHLIGHTED_FILE);
		if (!TextUtils.isEmpty(highlightedFile)) {
			mHighlightedFile = highlightedFile;
		}

		String startPath = getIntent().getStringExtra(START_PATH);
		if (!TextUtils.isEmpty(startPath)) {
			getDirectoryContents(new File(startPath));
		} else {
			finish();
		}
	}

	private void getDirectoryContents(File dirPath) {
		mFileList.clear();
		mPaths.clear();

		mPathView.setText(R.string.export_session_title);
		mPathViewHolder.post(() -> mPathViewHolder.fullScroll(View.FOCUS_RIGHT)); // note: no-longer needed, but kept in case

		File[] files = dirPath.listFiles();
		if (files == null) {
			Toast.makeText(SelectFileActivity.this, R.string.export_browser_error, Toast.LENGTH_SHORT).show();
			finish();
			return;
		}

		Arrays.sort(files, (file1, file2) -> -file1.getName().compareToIgnoreCase(file2.getName()));
		for (File file : files) {
			String fileName = file.getName();
			if (!fileName.startsWith(".") && !fileName.equals(TagScannerActivity.IMPORT_FILENAME) && !file.isDirectory()) {
				if (fileName.equals(mHighlightedFile)) {
					addItem("/b/" + getString(R.string.export_current_session));
				} else {
					try {
						Date fileDate = TagScannerActivity.FILE_TIMESTAMP_FORMAT.parse(fileName);
						if (fileDate == null) {
							throw new ParseException("No file date returned", 0);
						}
						addItem(FILE_DISPLAY_FORMAT.format(fileDate));
					} catch (ParseException e) {
						e.printStackTrace();
						addItem(fileName);
					}
				}
				mPaths.add(file.getAbsolutePath());
			}
		}

		StyledSimpleAdapter fileList = new StyledSimpleAdapter(this, mFileList, R.layout.select_file_row, new String[]{
				ITEM_KEY
		}, new int[]{ R.id.directory_selector_row });

		setListAdapter(fileList);
	}

	private void addItem(String text) {
		HashMap<String, String> item = new HashMap<>();
		item.put(ITEM_KEY, text);
		mFileList.add(item);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		File file = new File(mPaths.get(position));
		if (file.isDirectory()) {
			if (file.canRead()) {
				getDirectoryContents(file);
			} else {
				Toast.makeText(SelectFileActivity.this, R.string.export_browser_error, Toast.LENGTH_SHORT).show();
			}
		} else {
			Uri path = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
			Intent intent = new Intent(Intent.ACTION_SEND);
			intent.putExtra(Intent.EXTRA_TEXT, R.string.export_session_log);
			intent.putExtra(Intent.EXTRA_STREAM, path);
			intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			intent.setType(TagScannerActivity.CSV_MIME_TYPE);  // note: technically, "text/csv" (RFC 7111), but little supported
			startActivity(intent);
		}
	}

	private class StyledSimpleAdapter extends SimpleAdapter {
		StyledSimpleAdapter(Context context, List<? extends Map<String, ?>> data, int resource, String[] from, int[] to) {
			super(context, data, resource, from, to);
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			TextView textView;
			if (convertView == null) {
				LayoutInflater layoutInflater = LayoutInflater.from(SelectFileActivity.this);
				textView = (TextView) layoutInflater.inflate(R.layout.select_file_row, parent, false);
			} else {
				textView = (TextView) convertView;
			}

			textView.setTypeface(null, Typeface.NORMAL); // for reused views
			String text = mFileList.get(position).get(ITEM_KEY);
			if (!TextUtils.isEmpty(text)) {
				//noinspection ConstantConditions
				if (text.startsWith("/b/")) { // current session
					textView.setTypeface(null, Typeface.BOLD);
					text = text.substring(3);
				}
			}
			textView.setText(text);
			return textView;
		}
	}
}
