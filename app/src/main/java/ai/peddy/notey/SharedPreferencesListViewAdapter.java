package ai.peddy.notey;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SharedPreferencesListViewAdapter extends BaseAdapter {

    final private List<Map.Entry<String, Set<Long>>> noteIndex;

    public SharedPreferencesListViewAdapter(@NonNull Map<String, Set<Long>> notes) {
        this.noteIndex = new ArrayList<>();
        noteIndex.addAll(notes.entrySet());
    }

    @Override
    public int getCount() {
        return noteIndex.size();
    }

    @Override
    public Map.Entry<String, Set<Long>> getItem(int position) {
        return noteIndex.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final View result;

        if (convertView == null) {
            result = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.notes_listview_row, parent, false);
        } else {
            result = convertView;
        }

        Map.Entry<String, Set<Long>> notes = getItem(position);


        // Check for backward-compatibility with notes exported before adding delim
        String name = notes.getKey();
        if (name.split(NoteyService.TRACK_URI_NAME_DELIM).length == 2)
            name = name.split(NoteyService.TRACK_URI_NAME_DELIM)[1];

        ((TextView) result.findViewById(R.id.notes_lv_row_note_name)).setText(name);
        (result.findViewById(R.id.notes_lv_row_note_btn)).setTag(notes.getKey());

        return result;
    }
}
