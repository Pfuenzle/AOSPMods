
package sh.siava.AOSPMods.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import sh.siava.AOSPMods.R;

public class MaterialTimePickerPreference extends Preference {

	private String timeValue;

	public MaterialTimePickerPreference(Context context) {
		super(context);
	}

	public MaterialTimePickerPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context, attrs);
	}

	public MaterialTimePickerPreference(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context, attrs);
	}

	private void init(Context context, AttributeSet attrs) {
		setWidgetLayoutResource(R.layout.time_picker);
		if (attrs != null) {
			TypedArray a =
					context.getTheme().obtainStyledAttributes(attrs, R.styleable.MaterialTimePickerPreference, 0, 0);
			try {
				timeValue = a.getString(R.styleable.MaterialTimePickerPreference_presetValue);
			} finally {
				a.recycle();
			}
		}
	}

	@Override
	public void onBindViewHolder(PreferenceViewHolder holder) {
		super.onBindViewHolder(holder);
		TextView timeTextView = (TextView) holder.findViewById(R.id.time_stamp);
		timeTextView.setText(timeValue);
	}

	@Override
	protected void onClick() {
		super.onClick();

		MaterialTimePicker timePicker =
				new MaterialTimePicker.Builder().setTimeFormat(TimeFormat.CLOCK_24H).build();

		timePicker.addOnPositiveButtonClickListener(
				v -> {
					int hour = timePicker.getHour();
					int minute = timePicker.getMinute();
					String selectedTime = String.format("%02d:%02d", hour, minute);

					timeValue = selectedTime;
					persistString(selectedTime);

					notifyChanged();
				});

		timePicker.show(((AppCompatActivity) getContext()).getSupportFragmentManager(), "timePicker");
	}

	@Override
	protected Object onGetDefaultValue(TypedArray a, int index) {
		return a.getString(index);
	}

	@Override
	protected void onSetInitialValue(Object defaultValue) {
		timeValue = getPersistedString((String) defaultValue);
		persistString(timeValue);
	}

	public String getTimeValue() {
		return this.timeValue;
	}

	public void setTimeValue(String timeValue) {
		this.timeValue = timeValue;
		persistString(timeValue);
		notifyChanged();
	}
}