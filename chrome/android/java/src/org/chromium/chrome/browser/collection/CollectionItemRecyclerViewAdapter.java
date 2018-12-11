// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.collection;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.TrafficStats;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import org.chromium.base.Log;
import org.chromium.base.task.AsyncTask;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.collection.CollectionList.OnListFragmentInteractionListener;
import org.chromium.chrome.browser.collection.CollectionManager.CollectionItem;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.List;

/**
 * {@link RecyclerView.Adapter} that can display a {@link CollectionItem} and makes a call to the
 * specified {@link OnListFragmentInteractionListener}.
 */
public class CollectionItemRecyclerViewAdapter
        extends RecyclerView.Adapter<CollectionItemRecyclerViewAdapter.ViewHolder> {
    private static final String TAG = "CollectionItemAdaptr";

    private CollectionManager mCollectionManager;
    private final List<CollectionItem> mValues;
    private final OnListFragmentInteractionListener mListener;

    CollectionItemRecyclerViewAdapter(CollectionManager collectionManager,
            List<CollectionItem> items, OnListFragmentInteractionListener listener) {
        mCollectionManager = collectionManager;
        mValues = items;
        mListener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.collection_item, parent, false);
        return new ViewHolder(view);
    }

    static int findItemByImageUrl(List<CollectionItem> list, CollectionItem key) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).imageUrl.equals(key.imageUrl)) return i;
        }
        return -1;
    }

    void addOrUpdateItem(CollectionItem item) {
        int index = findItemByImageUrl(mValues, item);
        if (index < 0) {
            mValues.add(item);
            notifyItemInserted(mValues.size() - 1);
        } else {
            replaceItem(index, item);
        }
    }

    void updateItem(CollectionItem item) {
        int index = findItemByImageUrl(mValues, item);
        if (index < 0) return;
        replaceItem(index, item);
    }

    private void replaceItem(int index, CollectionItem item) {
        mValues.set(index, item);
        notifyItemChanged(index, item);
    }

    void removeItem(CollectionItem item) {
        int index = findItemByImageUrl(mValues, item);
        if (index < 0) return;
        mValues.remove(index);
        notifyItemRemoved(index);
    }

    private static class DownloadImageTask extends AsyncTask<Bitmap> {
        private WeakReference<ImageView> mImage;
        private String mUrl;

        DownloadImageTask(ImageView mImage, String url) {
            this.mImage = new WeakReference<>(mImage);
            mUrl = url;
        }

        @Override
        protected Bitmap doInBackground() {
            TrafficStats.setThreadStatsTag(0xff);

            Bitmap bmp = null;
            try {
                InputStream in = new java.net.URL(mUrl).openStream();
                bmp = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
                e.printStackTrace();
            }
            return bmp;
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            ImageView image = mImage.get();
            if (image != null) image.setImageBitmap(result);
        }
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.mItem = mValues.get(position);
        holder.mTitle.setText(mValues.get(position).title);
        holder.mPrice.setText(mValues.get(position).price);
        holder.mStar.setChecked(mValues.get(position).starred);
        holder.mStar.setOnClickListener(v -> {
            CheckBox checkBox = (CheckBox) v;
            holder.mItem.starred = checkBox.isChecked();
            if (null != mListener) {
                if (holder.mItem.starred) {
                    mCollectionManager.addStarredItem(holder.mItem);
                } else {
                    mCollectionManager.removeStarredItem(holder.mItem);
                }
            }
        });

        holder.mView.setOnClickListener(v -> {
            if (null != mListener) {
                // Notify the active callbacks interface (the activity, if the
                // fragment is attached to one) that an item has been selected.
                mListener.onListFragmentInteraction(holder.mItem);
            }
        });

        // TODO: Use payload to avoid image refreshing
        new DownloadImageTask(holder.mImage, mValues.get(position).imageUrl)
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public int getItemCount() {
        return mValues.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final TextView mTitle;
        public final TextView mPrice;
        public final CheckBox mStar;
        public final ImageView mImage;
        public CollectionItem mItem;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            mTitle = view.findViewById(R.id.title);
            mPrice = view.findViewById(R.id.price);
            mStar = view.findViewById(R.id.star);
            mImage = view.findViewById(R.id.thumbnail);
        }

        @Override
        public String toString() {
            return super.toString() + " " + mPrice.getText();
        }
    }
}
