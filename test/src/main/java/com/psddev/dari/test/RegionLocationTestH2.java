package com.psddev.dari.test;

import com.psddev.dari.db.Location;
import com.psddev.dari.db.Query;
import com.psddev.dari.db.Region;
import com.psddev.dari.util.ObjectUtils;
import org.junit.After;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class RegionLocationTestH2 extends RegionLocationTest {

    @Override
    @Test(expected = IllegalArgumentException.class)
    public void testGeoLocationQueryRegionWithLocation() throws Exception {
       super.testGeoLocationQueryRegionWithLocation();
    }


}

