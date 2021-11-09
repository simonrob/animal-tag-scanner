package ac.robinson.animaltagscanner;

import android.os.Parcel;
import android.os.Parcelable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.annotation.NonNull;

public class TagEntryModel implements Parcelable {
	private static final SimpleDateFormat CSV_ROW_TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-dd-MM HH:mm:ss", Locale.US);

	public String mTag;

	public long mTimeStamp; // of the most recent scan
	public String mSessionPerson; // set globally (TODO: allow editing? (won't update historical entries))
	public String mSessionPurpose; // set globally (TODO: allow editing? (won't update historical entries))

	public boolean mFeet; // defaults to global; editable locally
	public boolean mTeeth; // defaults to global; editable locally
	public boolean mUdder; // defaults to global; editable locally
	public boolean mDose; // defaults to global; editable locally

	public float mWeight; // TODO: type?
	public String mComments;

	public TagEntryModel() {
	}

	public static void addCSVHeaders(StringBuilder tagCSV) {
		tagCSV.append("Timestamp,Tag,Person,Purpose,Feet,Teeth,Udder,Weight,Dose,Comments\n");
	}

	public static String formatTimestamp(long timeStamp) {
		return CSV_ROW_TIMESTAMP_FORMAT.format(new Date(timeStamp));
	}

	private static String formatBoolean(boolean value) {
		return value ? "Yes" : "No";
	}

	public void toCSV(StringBuilder tagCSV, boolean checkFeet, boolean checkTeeth, boolean checkUdder, boolean recordWeight,
					  boolean recordDose) {
		tagCSV.append(formatTimestamp(mTimeStamp)).append(',');
		tagCSV.append(mTag).append(',');
		tagCSV.append(escapeCSVString(mSessionPerson)).append(',');
		tagCSV.append(escapeCSVString(mSessionPurpose)).append(',');
		tagCSV.append(checkFeet ? formatBoolean(mFeet) : "").append(',');
		tagCSV.append(checkTeeth ? formatBoolean(mTeeth) : "").append(',');
		tagCSV.append(checkUdder ? formatBoolean(mUdder) : "").append(',');
		tagCSV.append(recordWeight ? mWeight : "").append(',');
		tagCSV.append(recordDose ? formatBoolean(mDose) : "").append(',');
		tagCSV.append(escapeCSVString(mComments));
	}

	private String escapeCSVString(String originalValue) {
		String escapedValue = originalValue;
		if (escapedValue.contains(",") || escapedValue.contains("\n")) {
			escapedValue = '"' + escapedValue.replace("\"", "\"\"") + '"';
		}
		return escapedValue;
	}

	@NonNull
	@Override
	public String toString() {
		StringBuilder modelBuilder = new StringBuilder();
		toCSV(modelBuilder, true, true, true, true, true);
		return "TagEntryModel{" + modelBuilder.toString() + "}";
	}

	private TagEntryModel(Parcel in) {
		mTag = in.readString();
		mTimeStamp = in.readLong();
		mSessionPerson = in.readString();
		mSessionPurpose = in.readString();
		mFeet = in.readInt() == 1;
		mTeeth = in.readInt() == 1;
		mUdder = in.readInt() == 1;
		mDose = in.readInt() == 1;
		mWeight = in.readFloat();
		mComments = in.readString();
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(mTag);
		dest.writeLong(mTimeStamp);
		dest.writeString(mSessionPerson);
		dest.writeString(mSessionPurpose);
		dest.writeInt(mFeet ? 1 : 0);
		dest.writeInt(mTeeth ? 1 : 0);
		dest.writeInt(mUdder ? 1 : 0);
		dest.writeInt(mDose ? 1 : 0);
		dest.writeFloat(mWeight);
		dest.writeString(mComments);
	}

	// https://stackoverflow.com/questions/12503836/
	public static final Parcelable.Creator<TagEntryModel> CREATOR = new Parcelable.Creator<TagEntryModel>() {
		public TagEntryModel createFromParcel(Parcel in) {
			return new TagEntryModel(in);
		}

		public TagEntryModel[] newArray(int size) {
			return new TagEntryModel[size];
		}
	};
}
