/*
 * Copyright (C) 2010 Pixelpod INTERNATIONAL, Inc.
 * 
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package com.cyanogenmod.cmappschanger;

import com.cyanogenmod.cmappschanger.R;

import android.app.Activity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
/**
 * Insert comments
 * 
 * @author Timothy Caraballo
 * @version 0.8
 */
public class AppListAdapter extends ArrayAdapter<Object> {
    // Tag to use for logging
    private static final String TAG = "CM Apps Adapter";
    LayoutInflater inflater;
    String[] appNames = null;
    String[] appPaths = null;

    /**
     * Class constructor.
     * 
     * @param context The owner of this adapter.
     * @param apps   A <code>String[]</code> containing all the system app filenames.
     */
    AppListAdapter(Activity context, String[] apps) {
        super(context, R.layout.app_select, apps);

        inflater = context.getLayoutInflater();
        appNames = new String[apps.length];
        appPaths = new String[apps.length];
        System.arraycopy(apps, 0, this.appNames, 0, apps.length);

        for (int i = 0; i < apps.length; i++) {
            appPaths[i] = "/system/app/" + apps[i];
        }
    }

    /**
     * Returns the user-selected app paths
     * 
     * @return <code>String[]</code> of paths in same order as in the <code>ListView</code>
     */
    public String[] getPaths() {
        return appPaths;
    }
    
    /**
     * Returns the user-selected app path at the specified index
     * 
     * @param index index in list of apps 
     * @return app path
     */
    public String getPathAt(int index) {
        return appPaths[index];
    }
    
    /**
     * Returns the system app filenames
     * 
     * @return <code>String[]</code> of installed app filenames in same order as in the
     *          <code>ListView</code>
     */
    public String[] getApps() {
        return appNames;
    }

    /**
     * Sets a app at <code>position</code> to <code>path</code> to be applied later.
     * 
     * @param position Position in the <code>ListView</code> 
     * @param path     Full path of desired app.
     */
    public void setPathAt(int position, String path) {
        appPaths[position] = path;
        notifyDataSetChanged();
    }

    /**
     * Sets all apps desired paths at once to be applied later.
     * 
     * @param paths <code>String[]</code> of paths to be applied in same order as in the
     *               <code>ListView</code>
     */
    public void setPaths(String[] paths) {
        if (paths.length != appPaths.length) {
            // TODO: throw exception?
            Log.i(TAG, "Not resetting paths");
        } else {
            System.arraycopy(paths, 0, appPaths, 0, paths.length);
            notifyDataSetChanged();
        }
        
    }
    
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder = null;

        if (convertView == null) {    
            convertView = inflater.inflate(R.layout.app_select, null);
            
            holder = new ViewHolder();
            holder.app_name = (TextView) convertView.findViewById(R.id.app_name);
            holder.app_location = (TextView) convertView.findViewById(R.id.app_location);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        
        holder.app_name.setText(appNames[position]);
        // don't display app path if it's the system app
        if (appPaths[position].equals("/system/app/" + appNames[position])) {
            holder.app_location.setVisibility(View.GONE);
        } else {
            holder.app_location.setText(appPaths[position]);
            holder.app_location.setVisibility(View.VISIBLE);
        }
        return convertView;
    }
    
    /**
     * Class that holds frequently accessed references to reduce calls to <code>findViewById</code>.
     * 
     * @author Timothy Caraballo
     *
     */
    public static class ViewHolder {
        TextView app_name;
        TextView app_location;
    }
}
