package com.geekera1n.etherovo; // 确保包名一致

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import java.util.List;
import java.util.Objects;

class InfoItem {
    final String title;
    final String value;

    InfoItem(String title, String value) {
        this.title = title;
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InfoItem infoItem = (InfoItem) o;
        return Objects.equals(title, infoItem.title) && Objects.equals(value, infoItem.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, value);
    }
}

public class InfoAdapter extends ArrayAdapter<InfoItem> {

    public InfoAdapter(@NonNull Context context, @NonNull List<InfoItem> objects) {
        super(context, 0, objects);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = LayoutInflater.from(getContext()).inflate(R.layout.list_item_info, parent, false);
        }

        InfoItem item = getItem(position);
        if (item != null) {
            TextView titleView = view.findViewById(R.id.tvInfoTitle);
            TextView valueView = view.findViewById(R.id.tvInfoValue);

            titleView.setText(item.title);
            valueView.setText(item.value);

            if ("状态".equals(item.title)) {
                if ("UP".equals(item.value)) {
                    valueView.setTextColor(titleView.getTextColors());
                } else { // DOWN
                    valueView.setTextColor(ContextCompat.getColor(getContext(), R.color.error));
                }
            } else {
                valueView.setTextColor(ContextCompat.getColor(getContext(), R.color.onSurfaceVariant));
            }
        }
        return view;
    }
}