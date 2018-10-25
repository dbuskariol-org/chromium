// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.collection;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

import java.util.ArrayList;
import java.util.List;

public class CollectionPagerAdapter extends FragmentPagerAdapter {
    private List<CollectionList> mItems;

    CollectionPagerAdapter(FragmentManager fm, CollectionManager collectionManager) {
        super(fm);

        mItems = new ArrayList<>();
        mItems.add(CollectionList.newInstance(2, collectionManager, false));
        mItems.add(CollectionList.newInstance(2, collectionManager, true));
    }

    @Override
    public Fragment getItem(int position) {
        return mItems.get(position);
    }

    @Override
    public CharSequence getPageTitle(int position) {
        if (position == 0) {
            return "All";
        } else if (position == 1) {
            return "Starred";
        }
        return null;
    }

    @Override
    public int getCount() {
        return mItems.size();
    }

    /*
    //this is called when notifyDataSetChanged() is called
    @Override
    public int getItemPosition(Object object) {
        // refresh all fragments when data set changed
        return PagerAdapter.POSITION_NONE;
    }
    */
}
