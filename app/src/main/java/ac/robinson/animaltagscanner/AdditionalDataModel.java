package ac.robinson.animaltagscanner;

import androidx.annotation.NonNull;

class AdditionalDataModel {
	public String mAnimalName;
	public String mMotherName;
	public String mFatherName;
	public float mWeight;
	public String mComments;

	public AdditionalDataModel() {
	}

	public void setAnimalName(String animalName) {
		mAnimalName = animalName;
	}

	public void setMotherName(String motherName) {
		mMotherName = motherName;
	}

	public void setFatherName(String fatherName) {
		mFatherName = fatherName;
	}

	public void setWeight(float weight) {
		mWeight = weight;
	}

	public void setComments(String comments) {
		mComments = comments;
	}

	@NonNull
	@Override
	public String toString() {
		return "AdditionalDataModel{" + "mAnimalName='" + mAnimalName + '\'' + ", mMotherName='" + mMotherName + '\'' +
				", mFatherName='" + mFatherName + '\'' + ", mWeight=" + mWeight + ", mComments='" + mComments + '\'' + '}';
	}
}
