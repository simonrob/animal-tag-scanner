package ac.robinson.animaltagscanner;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

public class TagListAdapter extends RecyclerView.Adapter<TagListAdapter.TagViewHolder> {

	private final Activity mParentActivity;
	private RecyclerView mRecyclerView;
	private final ArrayList<TagEntryModel> mTagList;
	private HashMap<String, AdditionalDataModel> mAdditionalDataMap;

	private boolean mManualMode;
	private boolean mAutoShowAdditionalData;

	private final TagClickListener mTagClickListener;
	private final TagFocusChangeListener mTagFocusChangeListener;
	private final TagTextWatcher mTagTextWatcher;
	private final FeetCheckedChangeListener mFeetCheckedChangeListener;
	private final TeethCheckedChangeListener mTeethCheckedChangeListener;
	private final UdderCheckedChangeListener mUdderCheckedChangeListener;
	private final DoseCheckedChangeListener mDoseCheckedChangeListener;
	private final WeightTextWatcher mWeightTextWatcher;
	private final CommentFocusChangeListener mCommentFocusChangeListener;
	private final CommentTextWatcher mCommentTextWatcher;
	private final InfoClickListener mInfoClickListener;
	private final PhotoClickListener mPhotoClickListener;

	private TagViewHolder mCurrentViewHolder;

	private boolean mCheckFeet;
	private boolean mCheckTeeth;
	private boolean mCheckUdder;
	private boolean mRecordWeight;
	private boolean mRecordDose;

	public TagListAdapter(Activity activity, ArrayList<TagEntryModel> tagList) {
		mParentActivity = activity;
		mTagList = tagList;
		mTagClickListener = new TagClickListener();
		mTagFocusChangeListener = new TagFocusChangeListener();
		mTagTextWatcher = new TagTextWatcher();
		mFeetCheckedChangeListener = new FeetCheckedChangeListener();
		mTeethCheckedChangeListener = new TeethCheckedChangeListener();
		mUdderCheckedChangeListener = new UdderCheckedChangeListener();
		mDoseCheckedChangeListener = new DoseCheckedChangeListener();
		mWeightTextWatcher = new WeightTextWatcher();
		mCommentFocusChangeListener = new CommentFocusChangeListener();
		mCommentTextWatcher = new CommentTextWatcher();
		mInfoClickListener = new InfoClickListener();
		mPhotoClickListener = new PhotoClickListener();
	}

	@Override
	public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
		mRecyclerView = recyclerView;
		super.onAttachedToRecyclerView(recyclerView);
	}

	@NonNull
	@Override
	public TagViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
		View view = LayoutInflater.from(mParentActivity).inflate(R.layout.tag_entry, viewGroup, false);
		return new TagViewHolder(view, mTagClickListener, mTagFocusChangeListener, mTagTextWatcher, mFeetCheckedChangeListener,
				mTeethCheckedChangeListener, mUdderCheckedChangeListener, mDoseCheckedChangeListener, mWeightTextWatcher,
				mCommentFocusChangeListener, mCommentTextWatcher, mInfoClickListener, mPhotoClickListener);
	}

	private void updateCurrentViewHolder() {
		View first = mRecyclerView.getChildAt(0);
		int firstChild = mRecyclerView.getChildAdapterPosition(first);
		View last = mRecyclerView.getChildAt(mRecyclerView.getChildCount() - 1);
		int lastChild = mRecyclerView.getChildAdapterPosition(last);
		if (firstChild != lastChild) {
			mCurrentViewHolder = null;
		} else {
			mCurrentViewHolder = (TagViewHolder) mRecyclerView.findViewHolderForAdapterPosition(firstChild);
		}
	}

	public void updateAdditionalDataMap(HashMap<String, AdditionalDataModel> additionalDataMap) {
		mAdditionalDataMap = additionalDataMap;
	}

	public void configureDisplay(boolean manualMode, boolean autoShowAdditionalData, boolean feet, boolean teeth, boolean udder,
								 boolean weight, boolean dose) {
		mManualMode = manualMode;
		mAutoShowAdditionalData = autoShowAdditionalData;
		mCheckFeet = feet;
		mCheckTeeth = teeth;
		mCheckUdder = udder;
		mRecordDose = dose;
		mRecordWeight = weight;

		if (!mManualMode) {
			// TODO: can we get all views and do this for all of them?
			// TODO: (if one is focused and we switch away from it then disable manual mode then it is missed)
			mTagFocusChangeListener.onFocusChange(null, false);
		}
	}

	@Override
	public void onBindViewHolder(@NonNull TagViewHolder viewHolder, int position) {
		TagEntryModel currentEntry = mTagList.get(position);

		boolean emptyTag = TextUtils.isEmpty(currentEntry.mTag); // we focus as the last action to ensure it works
		setTagEditableDisplayMode(viewHolder, currentEntry.mTag, mManualMode && emptyTag, false);

		viewHolder.feet.setChecked(currentEntry.mFeet);
		viewHolder.teeth.setChecked(currentEntry.mTeeth);
		viewHolder.udder.setChecked(currentEntry.mUdder);
		viewHolder.dose.setChecked(currentEntry.mDose);
		viewHolder.weight.setText(currentEntry.mWeight == 0 ? "" : String.valueOf(currentEntry.mWeight));
		viewHolder.comments.setText(currentEntry.mComments);

		viewHolder.feet.setVisibility(mCheckFeet ? View.VISIBLE : View.GONE);
		viewHolder.teeth.setVisibility(mCheckTeeth ? View.VISIBLE : View.GONE);
		viewHolder.udder.setVisibility(mCheckUdder ? View.VISIBLE : View.GONE);
		viewHolder.dose.setVisibility(mRecordDose ? View.VISIBLE : View.GONE);
		viewHolder.checkLabel.setVisibility(
				(!mCheckFeet && !mCheckTeeth && !mCheckUdder & !mRecordDose) ? View.GONE : View.VISIBLE);
		viewHolder.weightLabel.setVisibility(mRecordWeight ? View.VISIBLE : View.GONE);
		viewHolder.weight.setVisibility(mRecordWeight ? View.VISIBLE : View.GONE);

		if (emptyTag) {
			focusView(viewHolder.tagEntry);
		} else if (mRecordWeight && !TextUtils.isEmpty(viewHolder.weight.getText())) {
			focusView(viewHolder.weight);
		} else {
			focusView(viewHolder.comments);
		}

		if (mAutoShowAdditionalData) {
			displayAdditionalData(currentEntry.mTag);
		}
	}

	@Override
	public void onViewAttachedToWindow(@NonNull TagViewHolder viewHolder) {
		updateCurrentViewHolder();
		super.onViewAttachedToWindow(viewHolder);
	}

	public void focusView(EditText view) {
		view.requestFocus(); // focuses the view but doesn't automatically show the keyboard (!)
		// view.getParent().requestChildFocus(view, view); // doesn't work at all
		view.post(() -> {
			if (view.requestFocus()) {
				mParentActivity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
				InputMethodManager inputMethodManager = (InputMethodManager) view.getContext()
						.getSystemService(Context.INPUT_METHOD_SERVICE);
				inputMethodManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
			}
		});
	}

	private void setTagEditableDisplayMode(TagViewHolder viewHolder, String tag, boolean editable, boolean requestFocus) {
		if (editable) {
			viewHolder.tagId.setVisibility(View.GONE);
			viewHolder.tagEntry.setVisibility(View.VISIBLE);
			viewHolder.tagEntry.setText(tag);
			if (requestFocus) {
				focusView(viewHolder.tagEntry);
			}
		} else {
			viewHolder.tagEntry.setVisibility(View.GONE);
			viewHolder.tagId.setVisibility(View.VISIBLE);
			viewHolder.tagId.setText(formatDisplayedTag(tag));
		}
	}

	private String formatDisplayedTag(String rawTag) {
		// TODO: make this a display preference?
		String scannedTag = TextUtils.isEmpty(rawTag) ? "" : rawTag;
		if (scannedTag.startsWith("826")) {
			// see: https://www.gov.uk/guidance/identify-livestock-for-export-to-the-eu-in-a-no-deal-brexit
			scannedTag = scannedTag.replaceFirst("826", "UK"); // 826 = UK ISO country code
		}
		int tagLength = scannedTag.length();
		if (tagLength > 5) {
			// codes are made up of a (seven-digit?) owner code followed by a (five-digit?) animal number
			// note that some tags do not follow this scheme - e.g., some animal numbers have letters in them
			scannedTag = scannedTag.substring(0, tagLength - 5) + "-" + scannedTag.substring(tagLength - 5);
		}
		return scannedTag;
	}

	private class TagClickListener implements View.OnClickListener {
		@Override
		public void onClick(View view) {
			updateCurrentViewHolder();
			if (mManualMode && mCurrentViewHolder != null) {
				int position = mCurrentViewHolder.getBindingAdapterPosition();
				if (position >= 0 && position < mTagList.size()) {
					setTagEditableDisplayMode(mCurrentViewHolder, mTagList.get(position).mTag, true, true);
				}
			}
		}
	}

	private class TagFocusChangeListener implements View.OnFocusChangeListener {
		@Override
		public void onFocusChange(View unused, boolean hasFocus) {
			updateCurrentViewHolder();
			if (mCurrentViewHolder != null) {
				if (hasFocus) {
					mCurrentViewHolder.tagEntry.setSelection(mCurrentViewHolder.tagEntry.getText().length());
				} else {
					String tag = mCurrentViewHolder.tagEntry.getText().toString();
					setTagEditableDisplayMode(mCurrentViewHolder, tag, mManualMode && TextUtils.isEmpty(tag), false);
				}
			}
		}
	}

	private class TagTextWatcher implements TextWatcher {
		@Override
		public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
		}

		@Override
		public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
		}

		@Override
		public void afterTextChanged(Editable editable) {
			updateCurrentViewHolder();
			int position = mCurrentViewHolder == null ? -1 : mCurrentViewHolder.getBindingAdapterPosition();
			if (position >= 0 && position < mTagList.size()) {
				mTagList.get(position).mTag = editable.toString();
			}
		}
	}

	private class FeetCheckedChangeListener implements CompoundButton.OnCheckedChangeListener {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			updateCurrentViewHolder();
			int position = mCurrentViewHolder == null ? -1 : mCurrentViewHolder.getBindingAdapterPosition();
			if (position >= 0 && position < mTagList.size()) {
				mTagList.get(position).mFeet = isChecked;
			}
		}
	}

	private class TeethCheckedChangeListener implements CompoundButton.OnCheckedChangeListener {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			updateCurrentViewHolder();
			int position = mCurrentViewHolder == null ? -1 : mCurrentViewHolder.getBindingAdapterPosition();
			if (position >= 0 && position < mTagList.size()) {
				mTagList.get(position).mTeeth = isChecked;
			}
		}
	}

	private class UdderCheckedChangeListener implements CompoundButton.OnCheckedChangeListener {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			updateCurrentViewHolder();
			int position = mCurrentViewHolder == null ? -1 : mCurrentViewHolder.getBindingAdapterPosition();
			if (position >= 0 && position < mTagList.size()) {
				mTagList.get(position).mUdder = isChecked;
			}
		}
	}

	private class DoseCheckedChangeListener implements CompoundButton.OnCheckedChangeListener {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			updateCurrentViewHolder();
			int position = mCurrentViewHolder == null ? -1 : mCurrentViewHolder.getBindingAdapterPosition();
			if (position >= 0 && position < mTagList.size()) {
				mTagList.get(position).mDose = isChecked;
			}
		}
	}

	private class WeightTextWatcher implements TextWatcher {
		@Override
		public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
		}

		@Override
		public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
		}

		@Override
		public void afterTextChanged(Editable editable) {
			updateCurrentViewHolder();
			int position = mCurrentViewHolder == null ? -1 : mCurrentViewHolder.getBindingAdapterPosition();
			if (position >= 0 && position < mTagList.size()) {
				String value = editable.toString();
				if (!TextUtils.isEmpty(value)) {
					try {
						mTagList.get(position).mWeight = Float.parseFloat(value);
					} catch (NumberFormatException e) {
						Snackbar.make(mParentActivity.findViewById(android.R.id.content),
								mParentActivity.getString(R.string.weight_entry_parse_error), Snackbar.LENGTH_SHORT).show();
					}
				}
			}
		}
	}

	private static class CommentFocusChangeListener implements View.OnFocusChangeListener {
		@Override
		public void onFocusChange(View view, boolean hasFocus) {
			if (hasFocus) {
				// TODO: is this actually required / useful for this particular EditText?
				((EditText) view).setSelection(((EditText) view).getText().length()); // set focus to the end of the text box
			}
		}
	}

	private class CommentTextWatcher implements TextWatcher {
		@Override
		public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
		}

		@Override
		public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
		}

		@Override
		public void afterTextChanged(Editable editable) {
			updateCurrentViewHolder();
			int position = mCurrentViewHolder == null ? -1 : mCurrentViewHolder.getBindingAdapterPosition();
			if (position >= 0 && position < mTagList.size()) {
				mTagList.get(position).mComments = editable.toString();
			}
		}
	}

	private class PhotoClickListener implements View.OnClickListener {
		@Override
		public void onClick(View view) {
			updateCurrentViewHolder();
			int position = mCurrentViewHolder == null ? -1 : mCurrentViewHolder.getBindingAdapterPosition();
			if (position >= 0 && position < mTagList.size()) {
				TagEntryModel currentEntry = mTagList.get(position);
				if (TextUtils.isEmpty(currentEntry.mTag)) {
					Snackbar.make(view, "Please enter a tag number before taking a photo", Snackbar.LENGTH_SHORT).show();
					return;
				}

				if (ContextCompat.checkSelfPermission(mParentActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
						PackageManager.PERMISSION_GRANTED) {

					// TODO: refactor this - select file first then use, e.g., https://stackoverflow.com/a/13069981/ ?
					// https://medium.com/android-news/androids-new-image-capture-from-a-camera-using-file-provider-dd178519a954
					// "TagPhotos" must match what is defined in provider_paths.xml
					// note also that we define android:requestLegacyExternalStorage="true" in the manifest to allow operation
					// (probably time-limited on Android Q and above)
					// TODO: unable to create TagPhotos directory on some phones despite permissions; had to switch to DCIM
					File imagesFolder = new File(Environment.getExternalStorageDirectory(), "DCIM");
					if (imagesFolder.exists() || imagesFolder.mkdirs()) {
						File image = new File(imagesFolder,
								currentEntry.mTag + "_" + TagScannerActivity.FILE_TIMESTAMP_FORMAT.format(new Date()) + ".jpg");
						Uri uriSavedImage = FileProvider.getUriForFile(mParentActivity,
								mParentActivity.getPackageName() + ".fileprovider", image);

						Intent imageIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
						imageIntent.putExtra(MediaStore.EXTRA_OUTPUT, uriSavedImage);
						mParentActivity.startActivity(imageIntent);
					} else {
						Snackbar.make(view, R.string.photo_error, Snackbar.LENGTH_SHORT).show();
					}

				} else {
					// TODO: is this okay (i.e., outside of activity?)
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
						mParentActivity.requestPermissions(new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE }, 123);
					}
				}
			}
		}
	}

	private class InfoClickListener implements View.OnClickListener {
		@Override
		public void onClick(View view) {
			updateCurrentViewHolder();
			int position = mCurrentViewHolder == null ? -1 : mCurrentViewHolder.getBindingAdapterPosition();
			if (position >= 0 && position < mTagList.size()) {
				TagEntryModel currentEntry = mTagList.get(position);
				if (TextUtils.isEmpty(currentEntry.mTag)) {
					Snackbar.make(view, R.string.additional_data_tag_missing, Snackbar.LENGTH_SHORT).show();
					return;
				}

				if (!displayAdditionalData(currentEntry.mTag)) {
					Snackbar.make(view, R.string.additional_data_empty, Snackbar.LENGTH_SHORT).show();
				}
			}
		}
	}

	private boolean displayAdditionalData(String tag) {
		if (mAdditionalDataMap != null) {
			AdditionalDataModel additionalData = mAdditionalDataMap.get(tag);
			if (additionalData != null) {
				AlertDialog.Builder builder = new AlertDialog.Builder(mParentActivity);
				builder.setTitle(mParentActivity.getString(R.string.additional_data_tag, formatDisplayedTag(tag)));
				View viewInflated = LayoutInflater.from(mParentActivity)
						.inflate(R.layout.additional_data_dialog, mParentActivity.findViewById(android.R.id.content), false);

				((TextView) viewInflated.findViewById(R.id.text_name)).setText(additionalData.mAnimalName);
				((TextView) viewInflated.findViewById(R.id.text_mother)).setText(additionalData.mMotherName);
				((TextView) viewInflated.findViewById(R.id.text_father)).setText(additionalData.mFatherName);
				((TextView) viewInflated.findViewById(R.id.text_weight)).setText(String.valueOf(additionalData.mWeight));
				((TextView) viewInflated.findViewById(R.id.text_additional_comments)).setText(additionalData.mComments);

				builder.setView(viewInflated);
				builder.setPositiveButton(R.string.done, null);
				builder.show();
				return true;
			}
		}
		return false;
	}

	@Override
	public int getItemCount() {
		return mTagList.size();
	}

	public static class TagViewHolder extends RecyclerView.ViewHolder {
		final TextView tagId;
		final EditText tagEntry;
		final TextView checkLabel;
		final CheckBox feet;
		final CheckBox teeth;
		final CheckBox udder;
		final CheckBox dose;
		final TextView weightLabel;
		final EditText weight;
		final EditText comments;
		final ImageButton info;
		final ImageButton photo;

		public TagViewHolder(View itemView, TagClickListener tagClickListener, TagFocusChangeListener tagFocusChangeListener,
							 TagTextWatcher tagTextWatcher, FeetCheckedChangeListener feetCheckedChangeListener,
							 TeethCheckedChangeListener teethCheckedChangeListener,
							 UdderCheckedChangeListener udderCheckedChangeListener,
							 DoseCheckedChangeListener doseCheckedChangeListener, WeightTextWatcher weightTextWatcher,
							 CommentFocusChangeListener commentFocusChangeListener, CommentTextWatcher commentTextWatcher,
							 InfoClickListener infoClickListener, PhotoClickListener photoClickListener) {
			super(itemView);
			tagId = itemView.findViewById(R.id.text_id);
			tagEntry = itemView.findViewById(R.id.tag_entry);
			checkLabel = itemView.findViewById(R.id.text_checking_label);
			feet = itemView.findViewById(R.id.feet_ok);
			teeth = itemView.findViewById(R.id.teeth_ok);
			udder = itemView.findViewById(R.id.udder_ok);
			dose = itemView.findViewById(R.id.dose_given);
			weightLabel = itemView.findViewById(R.id.text_weight_label);
			weight = itemView.findViewById(R.id.text_weight);
			comments = itemView.findViewById(R.id.text_comment);
			info = itemView.findViewById(R.id.button_info);
			photo = itemView.findViewById(R.id.button_photo);

			// inspired by https://stackoverflow.com/a/38422398/
			tagId.setOnClickListener(tagClickListener);
			tagEntry.setOnFocusChangeListener(tagFocusChangeListener);
			tagEntry.addTextChangedListener(tagTextWatcher);
			feet.setOnCheckedChangeListener(feetCheckedChangeListener);
			teeth.setOnCheckedChangeListener(teethCheckedChangeListener);
			udder.setOnCheckedChangeListener(udderCheckedChangeListener);
			dose.setOnCheckedChangeListener(doseCheckedChangeListener);
			weight.addTextChangedListener(weightTextWatcher);
			comments.setOnFocusChangeListener(commentFocusChangeListener);
			comments.addTextChangedListener(commentTextWatcher);
			info.setOnClickListener(infoClickListener);
			photo.setOnClickListener(photoClickListener);
		}
	}
}
