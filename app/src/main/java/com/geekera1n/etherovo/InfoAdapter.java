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

    // +++ 这是解决编译错误的关键修改 +++
    String originalValue;

    InfoItem(String title, String value) {
        this.title = title;
        this.value = value;
        this.originalValue = null; // 初始化新增的字段
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InfoItem infoItem = (InfoItem) o;
        // 列表更新时，我们只关心显示的值是否变化，所以这里只比较 title 和 value
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
            // 注意：请确保您的布局文件名和ID与这里一致
            view = LayoutInflater.from(getContext()).inflate(R.layout.list_item_info, parent, false);
        }

        InfoItem item = getItem(position);
        if (item != null) {
            // 注意：请确保您的TextView ID与这里一致
            TextView titleView = view.findViewById(R.id.tvInfoTitle);
            TextView valueView = view.findViewById(R.id.tvInfoValue);

            titleView.setText(item.title);
            valueView.setText(item.value);

            // 您的状态颜色逻辑保持不变
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