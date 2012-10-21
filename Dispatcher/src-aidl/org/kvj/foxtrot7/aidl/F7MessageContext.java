package org.kvj.foxtrot7.aidl;

import android.os.Parcel;
import android.os.Parcelable;

public class F7MessageContext implements Parcelable {

	public long id = 0;
	public long inResponse = 0;
	public String from = "";
	public String device = "";
	public long serie = 0;
	public boolean resend = false;

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeLong(id);
		dest.writeLong(inResponse);
		dest.writeString(from);
		dest.writeString(device);
		dest.writeLong(serie);
		dest.writeInt(resend ? 1 : 0);
	}

	public static final Parcelable.Creator<F7MessageContext> CREATOR = new Creator<F7MessageContext>() {

		@Override
		public F7MessageContext createFromParcel(Parcel source) {
			F7MessageContext obj = new F7MessageContext();
			obj.id = source.readLong();
			obj.inResponse = source.readLong();
			obj.from = source.readString();
			obj.device = source.readString();
			obj.serie = source.readLong();
			obj.resend = source.readInt() > 0;
			return obj;
		}

		@Override
		public F7MessageContext[] newArray(int size) {
			return new F7MessageContext[size];
		}
	};

	public F7MessageContext inResponse() {
		F7MessageContext ctx = new F7MessageContext();
		ctx.inResponse = this.id;
		ctx.device = this.device;
		return ctx;
	}

}
