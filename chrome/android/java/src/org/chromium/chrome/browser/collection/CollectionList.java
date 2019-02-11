// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.collection;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.base.Log;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.collection.CollectionManager.CollectionItem;

import java.util.List;

/**
 * A fragment representing a list of Items.
 * <p />
 * Activities containing this fragment MUST implement the {@link OnListFragmentInteractionListener}
 * interface.
 */
public class CollectionList extends Fragment {
    private static final String ARG_COLUMN_COUNT = "column-count";
    private static final String ARG_STARRED_TAB = "starred-tab";

    private int mColumnCount = 2;
    private boolean mIsStarredTab;

    private static CollectionManager sCollectionManager;
    private RecyclerView mView;
    private OnListFragmentInteractionListener mListener;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public CollectionList() {}

    public static CollectionList newInstance(
            int columnCount, CollectionManager collectionManager, boolean isStarredTab) {
        // TODO: get rid of this ugliness
        sCollectionManager = collectionManager;

        CollectionList fragment = new CollectionList();
        Bundle args = new Bundle();
        args.putInt(ARG_COLUMN_COUNT, columnCount);
        args.putBoolean(ARG_STARRED_TAB, isStarredTab);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mColumnCount = getArguments().getInt(ARG_COLUMN_COUNT);
            mIsStarredTab = getArguments().getBoolean(ARG_STARRED_TAB);
        }
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.collection_item_list, container, false);

        // Set the adapter
        if (view instanceof RecyclerView) {
            Context context = view.getContext();
            RecyclerView recyclerView = (RecyclerView) view;
            if (mColumnCount <= 1) {
                recyclerView.setLayoutManager(new LinearLayoutManager(context));
            } else {
                recyclerView.setLayoutManager(new GridLayoutManager(context, mColumnCount));
            }
            mView = recyclerView;
            // TODO: add two subclasses of CollectionList?
            List<CollectionItem> items;
            if (mIsStarredTab) {
                items = sCollectionManager.getStarredItems();
            } else {
                items = sCollectionManager.getAllItems();
            }
            Log.e("", "Collection item size = %d, starred = %b", items.size(), mIsStarredTab);
            CollectionItemRecyclerViewAdapter adapter =
                    new CollectionItemRecyclerViewAdapter(sCollectionManager, items, mListener);
            if (mIsStarredTab) {
                // TODO: allow drag reordering for starred tab:
                // https://medium.com/@ipaulpro/drag-and-swipe-with-recyclerview-b9456d2b1aaf
                sCollectionManager.setStarAdapter(adapter);
            } else {
                sCollectionManager.setAllAdapter(adapter);
            }
            recyclerView.setAdapter(adapter);
        }
        return view;
    }

    public int getVerticalScrollOffset() {
        return mView.computeVerticalScrollOffset();
    }

    public boolean getIsStarredTab() {
        return mIsStarredTab;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnListFragmentInteractionListener) {
            mListener = (OnListFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(
                    context.toString() + " must implement OnListFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    public interface OnListFragmentInteractionListener {
        void onListFragmentInteraction(CollectionItem item);
    }
}
