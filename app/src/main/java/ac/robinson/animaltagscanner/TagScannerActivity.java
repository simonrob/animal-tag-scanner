package ac.robinson.animaltagscanner;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaActionSound;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import com.google.android.material.snackbar.Snackbar;
import com.opencsv.CSVReaderHeaderAware;
import com.opencsv.exceptions.CsvValidationException;
import com.phidget22.ErrorEvent;
import com.phidget22.Phidget;
import com.phidget22.PhidgetException;
import com.phidget22.RFID;
import com.phidget22.RFIDTagEvent;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SnapHelper;

public class TagScannerActivity extends AppCompatActivity {

	private RFID mPhidgetRFIDScanner;

	private String mPersonName;
	private boolean mManualMode;
	private boolean mContinuousScanningMode;
	private boolean mAutoShowAdditionalData;
	private MediaActionSound mMediaActionSound;

	private boolean mAttachedStatus = false;
	private String statusText;

	public static final String CSV_MIME_TYPE = "text/*";  // note: technically, "text/csv" (RFC 7111), but little supported

	private static final int SELECT_CSV_IMPORT_FILE = 357;
	public static final String IMPORT_FILENAME = "imported_content.csv";

	@SuppressWarnings("SpellCheckingInspection") // note: timestamp display format based on user feedback
	public static final SimpleDateFormat FILE_TIMESTAMP_FORMAT = new SimpleDateFormat("yyyyddMM_HHmmss", Locale.US);
	private String mSessionFileName;

	RecyclerView mRecyclerView;
	private TagListAdapter mRecyclerViewAdapter;

	private ArrayList<TagEntryModel> mSessionTagList;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		try {
			mPhidgetRFIDScanner = new RFID();

			// allow direct USB connection to Phidget hardware
			if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST)) {
				com.phidget22.usb.Manager.Initialize(this);
			}

			mPhidgetRFIDScanner.addAttachListener(attachEvent -> {
				AttachEventHandler handler = new AttachEventHandler(mPhidgetRFIDScanner);
				runOnUiThread(handler);
			});

			mPhidgetRFIDScanner.addDetachListener(detachEvent -> {
				DetachEventHandler handler = new DetachEventHandler();
				runOnUiThread(handler);

			});

			mPhidgetRFIDScanner.addErrorListener(errorEvent -> {
				ErrorEventHandler handler = new ErrorEventHandler(errorEvent);
				runOnUiThread(handler);
			});

			mPhidgetRFIDScanner.addTagListener(tagEvent -> {
				RFIDTagEventHandler handler = new RFIDTagEventHandler(mPhidgetRFIDScanner, tagEvent);
				runOnUiThread(handler);
			});

			mPhidgetRFIDScanner.addTagLostListener(e -> Log.d("TAG-LOST", e.toString()));
			mPhidgetRFIDScanner.addPropertyChangeListener(e -> Log.d("PROP-CHANGE", e.toString()));
			mPhidgetRFIDScanner.open();
		} catch (PhidgetException pe) {
			pe.printStackTrace();
			Snackbar.make(findViewById(android.R.id.content), R.string.scanner_connection_error, Snackbar.LENGTH_SHORT).show();
		}

		SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);
		mPersonName = preferences.getString("mPersonName", "");
		mManualMode = preferences.getBoolean("mManualMode", true);
		if (mManualMode) {
			mContinuousScanningMode = false;
		} else {
			mContinuousScanningMode = preferences.getBoolean("mContinuousScanningMode", false);
		}
		mAutoShowAdditionalData = preferences.getBoolean("mAutoShowAdditionalData", true);

		if (savedInstanceState != null && savedInstanceState.containsKey("mSessionTagList")) {
			mSessionTagList = savedInstanceState.getParcelableArrayList("mSessionTagList");
		} else {
			mSessionTagList = new ArrayList<>();
		}

		mRecyclerView = findViewById(R.id.recycler_view);
		mRecyclerViewAdapter = new TagListAdapter(TagScannerActivity.this, mSessionTagList);
		// mRecyclerView.setLayoutManager(new SmoothScrollLayoutManager(TagScannerActivity.this));
		mRecyclerView.setAdapter(mRecyclerViewAdapter);
		mRecyclerView.setItemAnimator(new DefaultItemAnimator());
		SnapHelper snapHelper = new PagerSnapHelper();
		snapHelper.attachToRecyclerView(mRecyclerView);
		updateViewSwitcher(); // so we re-show if there is existing data

		mMediaActionSound = new MediaActionSound();
		mMediaActionSound.load(MediaActionSound.SHUTTER_CLICK);

		((CheckBox) findViewById(R.id.check_feet)).setOnCheckedChangeListener(mGlobalCheckedChangeListener);
		((CheckBox) findViewById(R.id.check_teeth)).setOnCheckedChangeListener(mGlobalCheckedChangeListener);
		((CheckBox) findViewById(R.id.check_udder)).setOnCheckedChangeListener(mGlobalCheckedChangeListener);
		((CheckBox) findViewById(R.id.record_weight)).setOnCheckedChangeListener(mGlobalCheckedChangeListener);
		((CheckBox) findViewById(R.id.record_dose)).setOnCheckedChangeListener(mGlobalCheckedChangeListener);

		findViewById(R.id.button_scan).setOnClickListener(v -> {
			try {
				if (mManualMode) {
					parseScannedTag("");
				} else {
					mPhidgetRFIDScanner.setAntennaEnabled(true);
					findViewById(R.id.scan_progress).setVisibility(View.VISIBLE);
				}
			} catch (PhidgetException e) {
				Snackbar.make(findViewById(android.R.id.content), R.string.scanner_start_error, Snackbar.LENGTH_SHORT).show();
				e.printStackTrace();
			}
		});

		if (mSessionTagList.size() > 0) {
			// TODO: is there a better behaviour here? (clearly yes...)
			findViewById(R.id.text_purpose).setEnabled(false);
			findViewById(R.id.check_feet).setEnabled(false);
			findViewById(R.id.check_teeth).setEnabled(false);
			findViewById(R.id.check_udder).setEnabled(false);
			findViewById(R.id.record_weight).setEnabled(false);
			findViewById(R.id.record_dose).setEnabled(false);
			findViewById(R.id.record_dose_comment).setEnabled(false);
		}

		loadImportedContent();
		updateAdapterDisplay();
	}

	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState) {
		outState.putParcelableArrayList("mSessionTagList", mSessionTagList); // https://stackoverflow.com/a/12503875/
		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onPause() {
		saveCSV();
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		try {
			mPhidgetRFIDScanner.close();
		} catch (PhidgetException ignored) {
		}

		// disable USB connection to Phidgets
		if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST)) {
			com.phidget22.usb.Manager.Uninitialize();
		}

		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu, menu);
		if (BuildConfig.DEBUG) {
			new Handler().post(() -> {
				final View view = findViewById(R.id.action_settings);
				if (view != null) {
					view.setOnLongClickListener(v -> {
						parseScannedTag("826050689500386");
						parseScannedTag("826000000000000");
						return true;
					});
				}
			});
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int itemId = item.getItemId();
		if (itemId == R.id.action_settings) {
			AlertDialog.Builder builder = new AlertDialog.Builder(TagScannerActivity.this);
			builder.setTitle(R.string.label_scanner_settings);
			View viewInflated = getLayoutInflater().inflate(R.layout.settings_dialog, findViewById(android.R.id.content), false);

			((TextView) viewInflated.findViewById(R.id.scanner_info)).setText(
					mAttachedStatus ? statusText : getString(R.string.info_scanner_detached));
			EditText personInput = viewInflated.findViewById(R.id.text_person);
			personInput.setText(mPersonName);
			SwitchCompat modeSwitch = viewInflated.findViewById(R.id.switch_manual);
			modeSwitch.setChecked(mManualMode);
			SwitchCompat continuousSwitch = viewInflated.findViewById(R.id.switch_continuous);
			continuousSwitch.setChecked(mContinuousScanningMode);
			SwitchCompat autoShowSwitch = viewInflated.findViewById(R.id.switch_show_additional_data);
			autoShowSwitch.setChecked(mAutoShowAdditionalData);

			viewInflated.findViewById(R.id.button_import_additional_data).setOnClickListener(v -> {
				// TODO: handle pre-API 19 version of this
				if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
					Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
					intent.addCategory(Intent.CATEGORY_OPENABLE);
					intent.setType(CSV_MIME_TYPE);
					startActivityForResult(intent, SELECT_CSV_IMPORT_FILE);
				} else {
					Snackbar.make(findViewById(android.R.id.content), R.string.import_file_error, Snackbar.LENGTH_SHORT).show();
				}
			});

			viewInflated.findViewById(R.id.button_export_additional_data).setOnClickListener(v -> exportImportedContent());

			modeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
				if (isChecked) {
					continuousSwitch.setChecked(false);
					continuousSwitch.setEnabled(false);
				} else {
					continuousSwitch.setEnabled(true);
				}
			});

			builder.setView(viewInflated);
			builder.setPositiveButton(android.R.string.ok,
					(dialog, which) -> updateAppSettings(personInput, modeSwitch, continuousSwitch, autoShowSwitch, dialog));
			builder.setNegativeButton(android.R.string.cancel, null);

			AlertDialog dialog = builder.show();
			personInput.setOnEditorActionListener((v, actionId, event) -> {
				if (actionId == EditorInfo.IME_ACTION_DONE) {
					updateAppSettings(personInput, modeSwitch, continuousSwitch, autoShowSwitch, dialog);
					return true;
				}
				return false;
			});

			return true;

		} else if (itemId == R.id.action_share) {
			saveCSV();

			Intent intent = new Intent(getBaseContext(), SelectFileActivity.class);
			intent.putExtra(SelectFileActivity.START_PATH, getFilesDir().getAbsolutePath());
			intent.putExtra(SelectFileActivity.HIGHLIGHTED_FILE, mSessionFileName);
			startActivity(intent);

			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void updateAppSettings(TextView personInput, SwitchCompat modeSwitch, SwitchCompat continuousSwitch,
								   SwitchCompat autoShowSwitch, DialogInterface dialog) {
		mPersonName = personInput.getText().toString();
		mManualMode = modeSwitch.isChecked();
		mContinuousScanningMode = continuousSwitch.isChecked();
		mAutoShowAdditionalData = autoShowSwitch.isChecked();
		SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = preferences.edit();
		editor.putString("mPersonName", mPersonName);
		editor.putBoolean("mManualMode", mManualMode);
		editor.putBoolean("mContinuousScanningMode", mContinuousScanningMode);
		editor.putBoolean("mAutoShowAdditionalData", mAutoShowAdditionalData);
		editor.apply();
		dialog.dismiss();

		if (!mManualMode) { // remove any unfinished items
			// TODO: combine with initial creation in parseScannedTag()
			String globalComments = ((EditText) findViewById(R.id.record_dose_comment)).getText().toString();
			if (!TextUtils.isEmpty(globalComments)) {
				globalComments += "\n";
			}

			ArrayList<TagEntryModel> removedItems = new ArrayList<>();
			for (TagEntryModel tag : mSessionTagList) {
				if (TextUtils.isEmpty(tag.mTag) && TextUtils.equals(tag.mComments, globalComments)) {
					removedItems.add(tag);
				}
			}
			for (TagEntryModel tag : removedItems) {
				int deletedItem = mSessionTagList.indexOf(tag);
				mSessionTagList.remove(tag);
				mRecyclerViewAdapter.notifyItemRemoved(deletedItem);
			}
		}

		updateAdapterDisplay();

		try {
			mPhidgetRFIDScanner.setAntennaEnabled(mContinuousScanningMode);
		} catch (PhidgetException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
		if (requestCode == SELECT_CSV_IMPORT_FILE && resultCode == Activity.RESULT_OK) {
			if (data != null) {
				Uri uri = data.getData();
				InputStream inputStream = null;
				FileOutputStream fileOutputStream = null;
				try {
					inputStream = getContentResolver().openInputStream(uri);
					fileOutputStream = openFileOutput(IMPORT_FILENAME, Context.MODE_PRIVATE);
					copyFile(inputStream, fileOutputStream);
					int count = loadImportedContent();
					Snackbar.make(findViewById(android.R.id.content), getString(R.string.import_success, count),
							Snackbar.LENGTH_SHORT).show();
					return;
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					closeStream(inputStream);
					closeStream(fileOutputStream);
				}
				Snackbar.make(findViewById(android.R.id.content), R.string.import_error, Snackbar.LENGTH_SHORT).show();
			}
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	private static final int IO_BUFFER_SIZE = 4 * 1024;

	private static void copyFile(InputStream in, OutputStream out) throws IOException {
		byte[] buf = new byte[IO_BUFFER_SIZE];
		int len;
		while ((len = in.read(buf)) > 0) {
			out.write(buf, 0, len);
		}
	}

	private static void closeStream(Closeable stream) {
		if (stream != null) {
			try {
				stream.close();
			} catch (Throwable ignored) {
			}
		}
	}

	@Override
	public void onBackPressed() {
		if (mSessionFileName != null) {
			new AlertDialog.Builder(this).setTitle(R.string.confirm_exit_title)
					.setMessage(getString(R.string.confirm_exit_message, mSessionFileName))
					.setPositiveButton(R.string.confirm_exit_yes, (dialog, whichButton) -> {
						saveCSV();
						int count = mSessionTagList.size();
						mSessionTagList.clear();
						mRecyclerViewAdapter.notifyItemRangeRemoved(0, count);

						CheckBox checkFeet = findViewById(R.id.check_feet);
						checkFeet.setChecked(false);
						checkFeet.setEnabled(true);
						CheckBox checkTeeth = findViewById(R.id.check_teeth);
						checkTeeth.setChecked(false);
						checkTeeth.setEnabled(true);
						CheckBox checkUdder = findViewById(R.id.check_udder);
						checkUdder.setChecked(false);
						checkUdder.setEnabled(true);
						CheckBox recordWeight = findViewById(R.id.record_weight);
						recordWeight.setChecked(false);
						recordWeight.setEnabled(true);
						CheckBox recordDose = findViewById(R.id.record_dose);
						recordDose.setChecked(false);
						recordDose.setEnabled(true);

						TextView textPurpose = findViewById(R.id.text_purpose);
						textPurpose.setText(null);
						textPurpose.setEnabled(true);
						TextView textDose = findViewById(R.id.record_dose_comment);
						textDose.setText(null);
						textDose.setEnabled(true);

						mSessionFileName = null;
					})
					.setNegativeButton(R.string.confirm_exit_no, null)
					.show();
		} else {
			super.onBackPressed();
		}
	}

	private void saveCSV() {
		// TODO: use openCSV?
		if (mSessionFileName != null) {
			boolean checkFeet = ((CheckBox) findViewById(R.id.check_feet)).isChecked();
			boolean checkTeeth = ((CheckBox) findViewById(R.id.check_teeth)).isChecked();
			boolean checkUdder = ((CheckBox) findViewById(R.id.check_udder)).isChecked();
			boolean recordWeight = ((CheckBox) findViewById(R.id.record_weight)).isChecked();
			boolean recordDose = ((CheckBox) findViewById(R.id.record_dose)).isChecked();

			StringBuilder tagCSV = new StringBuilder();
			TagEntryModel.addCSVHeaders(tagCSV);
			for (TagEntryModel tag : mSessionTagList) {
				tag.toCSV(tagCSV, checkFeet, checkTeeth, checkUdder, recordWeight, recordDose);
				tagCSV.append("\n");
			}

			FileOutputStream fileOutputStream = null;
			try {
				fileOutputStream = openFileOutput(mSessionFileName, Context.MODE_PRIVATE);
				fileOutputStream.write(tagCSV.toString().getBytes());
			} catch (IOException e) {
				Snackbar.make(findViewById(android.R.id.content), getString(R.string.file_save_error, mSessionFileName),
						Snackbar.LENGTH_SHORT).show();
			} finally {
				closeStream(fileOutputStream);
			}
		}
	}

	private final CompoundButton.OnCheckedChangeListener mGlobalCheckedChangeListener =
			(buttonView, isChecked) -> updateAdapterDisplay();

	private void updateAdapterDisplay() {
		boolean feet = ((CheckBox) findViewById(R.id.check_feet)).isChecked();
		boolean teeth = ((CheckBox) findViewById(R.id.check_teeth)).isChecked();
		boolean udder = ((CheckBox) findViewById(R.id.check_udder)).isChecked();
		boolean weight = ((CheckBox) findViewById(R.id.record_weight)).isChecked();
		boolean dose = ((CheckBox) findViewById(R.id.record_dose)).isChecked();
		mRecyclerViewAdapter.configureDisplay(mManualMode, mAutoShowAdditionalData, feet, teeth, udder, weight, dose);
	}

	private int loadImportedContent() {
		File file = getFileStreamPath(IMPORT_FILENAME);
		if (file == null || !file.exists()) {
			// file doesn't exist - create an example from resources
			InputStream inputStream = null;
			FileOutputStream fileOutputStream = null;
			try {
				inputStream = getResources().openRawResource(R.raw.imported_content);
				fileOutputStream = openFileOutput(IMPORT_FILENAME, Context.MODE_PRIVATE);
				copyFile(inputStream, fileOutputStream);
			} catch (FileNotFoundException ignored) { // TODO: warn about these exceptions?
			} catch (IOException ignored) {
			} finally {
				closeStream(inputStream);
				closeStream(fileOutputStream);
			}
			// note: we don't actually load anything here as the content is all example data - it *will* be loaded next time
			// though (but is okay since it is example content and unlikely to match any real animal tags)
			return 0;
		}

		FileInputStream in = null;
		try {
			in = openFileInput(IMPORT_FILENAME);
			BufferedReader r = new BufferedReader(new InputStreamReader(in));

			Map<String, String> values;
			HashMap<String, AdditionalDataModel> importedDataMap = new HashMap<>();
			CSVReaderHeaderAware csvReaderHeaderAware = new CSVReaderHeaderAware(r);
			int count = 0;
			while (((values = (Map<String, String>) csvReaderHeaderAware.readMap())) != null) {
				AdditionalDataModel additionalDataModel = new AdditionalDataModel();
				String switchKey;
				String itemValue;
				String animalTag = null;
				for (String key : values.keySet()) {
					switchKey = key.toLowerCase();
					itemValue = values.get(key);
					if (TextUtils.isEmpty(itemValue)) {
						continue;
					}
					switch (switchKey) {
						case "tag":
							animalTag = itemValue;
							break;
						case "name":
							additionalDataModel.setAnimalName(itemValue);
							break;
						case "mother":
							additionalDataModel.setMotherName(itemValue);
							break;
						case "father":
							additionalDataModel.setFatherName(itemValue);
							break;
						case "weight":
							try {
								//noinspection ConstantConditions
								additionalDataModel.setWeight(Float.parseFloat(itemValue));
							} catch (NumberFormatException ignored) {
							}
							break;
						case "comments":
							additionalDataModel.setComments(values.get(key));
							break;
						default:
							break;
					}
				}
				if (animalTag != null) {
					importedDataMap.put(animalTag, additionalDataModel);
					count += 1;
				}
			}
			mRecyclerViewAdapter.updateAdditionalDataMap(importedDataMap);
			return count;

		} catch (FileNotFoundException ignored) { // TODO: warn about these exceptions?
		} catch (IOException ignored) {
		} catch (CsvValidationException ignored) {
		} finally {
			closeStream(in);
		}
		return 0;
	}

	private void exportImportedContent() {
		Uri path = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", new File(getFilesDir(),
				IMPORT_FILENAME));
		Intent intent = new Intent(Intent.ACTION_SEND);
		intent.putExtra(Intent.EXTRA_TEXT, "Export contextual information");
		intent.putExtra(Intent.EXTRA_STREAM, path);
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		intent.setType(
				TagScannerActivity.CSV_MIME_TYPE);  // note: technically, "text/csv" (RFC 7111), but little support for this
		startActivity(intent);
	}

	private void parseScannedTag(String scannedTag) {
		if (TextUtils.isEmpty(scannedTag)) { // manual entry mode - only allow one blank entry at once
			for (TagEntryModel tag : mSessionTagList) {
				if (TextUtils.isEmpty(tag.mTag)) {
					// TODO: this is pretty awful - refactor/rewrite
					int itemIndex = mSessionTagList.indexOf(tag);
					RecyclerView.ViewHolder viewHolder = mRecyclerView.findViewHolderForAdapterPosition(itemIndex);
					int currentItemPosition = -1;
					if (viewHolder != null) {
						currentItemPosition = viewHolder.getBindingAdapterPosition();
					}
					if (currentItemPosition != itemIndex) {
						// TODO: smoothScrollToPosition is buggy so avoided elsewhere, but here we need the animation as a hint
						mRecyclerView.smoothScrollToPosition(itemIndex);
						if (viewHolder != null) {
							mRecyclerViewAdapter.focusView(((TagListAdapter.TagViewHolder) viewHolder).tagEntry);
						}
					} else {
						Snackbar.make(findViewById(android.R.id.content), R.string.manual_entry_empty, Snackbar.LENGTH_SHORT)
								.show();
					}
					return;
				}
			}
		}

		if (mSessionFileName == null) {
			mSessionFileName = FILE_TIMESTAMP_FORMAT.format(new Date()) + ".csv";
		}

		// TODO: is there a better behaviour here? (clearly yes...)
		findViewById(R.id.text_purpose).setEnabled(false);
		findViewById(R.id.check_feet).setEnabled(false);
		findViewById(R.id.check_teeth).setEnabled(false);
		findViewById(R.id.check_udder).setEnabled(false);
		findViewById(R.id.record_weight).setEnabled(false);
		findViewById(R.id.record_dose).setEnabled(false);
		findViewById(R.id.record_dose_comment).setEnabled(false);

		findViewById(R.id.scan_progress).setVisibility(View.GONE);

		TagEntryModel itemModel = null;
		for (TagEntryModel tag : mSessionTagList) {
			if (tag.mTag.equals(scannedTag)) {
				itemModel = tag;
				break;
			}
		}

		if (itemModel == null) {
			itemModel = new TagEntryModel();
			itemModel.mTag = scannedTag;
			itemModel.mTimeStamp = System.currentTimeMillis();
			itemModel.mSessionPerson = mPersonName;
			itemModel.mSessionPurpose = ((EditText) findViewById(R.id.text_purpose)).getText().toString();
			itemModel.mFeet = ((CheckBox) findViewById(R.id.check_feet)).isChecked();
			itemModel.mTeeth = ((CheckBox) findViewById(R.id.check_teeth)).isChecked();
			itemModel.mUdder = ((CheckBox) findViewById(R.id.check_udder)).isChecked();
			itemModel.mDose = ((CheckBox) findViewById(R.id.record_dose)).isChecked();
			itemModel.mComments = ((EditText) findViewById(R.id.record_dose_comment)).getText().toString();
			if (!TextUtils.isEmpty(itemModel.mComments)) {
				itemModel.mComments += "\n";
			}

			mSessionTagList.add(itemModel);
			int newPosition = mRecyclerViewAdapter.getItemCount() - 1;
			mRecyclerViewAdapter.notifyItemInserted(newPosition);
			mRecyclerView.scrollToPosition(newPosition); // smoothScrollToPosition is buggy
		} else {
			int itemIndex = mSessionTagList.indexOf(itemModel);
			itemModel.mComments += (itemModel.mComments.endsWith("\n") ? "" : "\n") +
					getString(R.string.previous_tag_record, TagEntryModel.formatTimestamp(itemModel.mTimeStamp));
			itemModel.mTimeStamp = System.currentTimeMillis();
			mRecyclerViewAdapter.notifyItemChanged(itemIndex);
			mRecyclerView.scrollToPosition(itemIndex); // smoothScrollToPosition is buggy
		}

		updateViewSwitcher();
		saveCSV();
	}

	private void updateViewSwitcher() {
		ViewSwitcher viewSwitcher = findViewById(R.id.switcher);
		if (mSessionTagList.size() > 0) {
			if (viewSwitcher.getNextView().getId() == R.id.recycler_view) {
				viewSwitcher.showNext();
			}
		} else if (viewSwitcher.getNextView().getId() == R.id.empty_view) {
			viewSwitcher.showNext();
		}
	}

	private class AttachEventHandler implements Runnable {
		final Phidget mPhidget;

		public AttachEventHandler(Phidget phidget) {
			mPhidget = phidget;
		}

		public void run() {
			mAttachedStatus = true;
			try {
				statusText = getString(
						mManualMode ? R.string.info_scanner_attached_manual_entry_disabled : R.string.info_scanner_attached,
						mPhidget.getDeviceName(), mPhidget.getDeviceSerialNumber(), mPhidget.getDeviceVersion(),
						mPhidget.getChannel(), mPhidget.getHubPort(), mPhidget.getDeviceLabel());
				mManualMode = false;
				Snackbar.make(findViewById(android.R.id.content), statusText, Snackbar.LENGTH_SHORT).show();
				((RFID) mPhidget).setAntennaEnabled(false); // we require at least one press to start
			} catch (PhidgetException e) {
				e.printStackTrace();
			}

			synchronized (this) {
				this.notify(); // TODO: necessary?
			}
		}
	}

	private class DetachEventHandler implements Runnable {
		public void run() {
			mAttachedStatus = false;
			Snackbar.make(findViewById(android.R.id.content), R.string.info_scanner_detached, Snackbar.LENGTH_SHORT).show();

			synchronized (this) {
				this.notify(); // TODO: necessary?
			}
		}
	}

	private class ErrorEventHandler implements Runnable {
		final ErrorEvent mErrorEvent;

		public ErrorEventHandler(ErrorEvent errorEvent) {
			mErrorEvent = errorEvent;
		}

		public void run() {
			Snackbar.make(findViewById(android.R.id.content), mErrorEvent.getDescription(), Snackbar.LENGTH_SHORT).show();
		}
	}

	private class RFIDTagEventHandler implements Runnable {
		final Phidget mPhidget;
		final RFIDTagEvent mTagEvent;

		public RFIDTagEventHandler(Phidget phidget, RFIDTagEvent tagEvent) {
			mPhidget = phidget;
			mTagEvent = tagEvent;
		}

		// id = mTagEvent.getTag();
		// protocol = mTagEvent.getProtocol().getMessage();
		public void run() {
			Log.d("TAG", "TagEvent: " + mTagEvent);
			mMediaActionSound.play(MediaActionSound.SHUTTER_CLICK);
			parseScannedTag(mTagEvent.getTag());

			// note: fires "tag off" event too
			try {
				((RFID) mPhidget).setAntennaEnabled(mContinuousScanningMode);
			} catch (PhidgetException e) {
				e.printStackTrace();
			}
		}
	}
}

