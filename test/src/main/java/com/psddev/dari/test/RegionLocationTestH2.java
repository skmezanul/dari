package com.psddev.dari.test;

import org.junit.Test;

public class RegionLocationTestH2 extends RegionLocationTest {

    @Override
    @Test(expected = IllegalArgumentException.class)
    public void testGeoLocationQueryRegionWithLocation() throws Exception {
       super.testGeoLocationQueryRegionWithLocation();
    }
}

