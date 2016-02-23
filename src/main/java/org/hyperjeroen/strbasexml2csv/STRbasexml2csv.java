/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 hyperjeroen
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.hyperjeroen.strbasexml2csv;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.xml.bind.JAXB;

import generated.Frequencies;

/**
 * Converts the contents of the STRbase xml to a number of origin-specific CSV files.
 */
public class STRbasexml2csv
{
    public static void main(final String[] args) throws IOException
    {
        File strbaseFile = null;
        if (args.length > 0)
            strbaseFile = new File(args[0]);
        else {
            final JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Please select the STRbase xml file");
            if (JOptionPane.OK_OPTION == chooser.showOpenDialog(null)) {
                strbaseFile = chooser.getSelectedFile();
            }
        }

        final ArrayList<String> markers = new ArrayList<String>();
        final ArrayList<String> origins = new ArrayList<String>();
        final ArrayList<String> alleles = new ArrayList<String>();
        final HashMap<String, BigInteger> sampleSizes = new HashMap<String, BigInteger>();
        final HashMap<String, Float> allFrequencies = new HashMap<String, Float>();

        final Frequencies freqs = JAXB.unmarshal(strbaseFile, Frequencies.class);
        final StringBuilder headerLineBuilder = new StringBuilder("\"Allele\"");
        for (final Frequencies.Marker marker : freqs.getMarker()) {
            if (!marker.getAlleles().isEmpty()) {
                headerLineBuilder.append(",\"").append(marker.getName()).append("\"");
                for (final String allele : marker.getAlleles().split(", ")) {
                    if (!allele.equals("1") && !allele.equals("99")) {
                        if (!alleles.contains(allele)) {
                            alleles.add(allele);
                        }
                    }
                }
                if (!markers.contains(marker.getName())) {
                    markers.add(marker.getName());
                }
                for (final Frequencies.Marker.Origin origin : marker.getOrigin()) {
                    sampleSizes.put(origin.getName() + "/" + marker.getName(), origin.getN());
                    for (final Frequencies.Marker.Origin.Frequency frequency : origin.getFrequency()) {
                        if (!origins.contains(origin.getName())) {
                            origins.add(origin.getName());
                        }
                        allFrequencies.put(origin.getName() + "/" + marker.getName() + "/" + frequency.getAllele(), frequency.getValue());
                    }
                }
            }
        }

        Collections.sort(alleles, new Comparator<String>() {
            public int compare(final String o1, final String o2) {
                return new Float(o1).compareTo(new Float(o2));
            }
        });

        final String headerLine = headerLineBuilder.append("\n").toString();

        for (final String origin : origins) {
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(new File(strbaseFile.getParentFile(), strbaseFile.getName().replace(".xml", "_" + origin + ".csv")), false);
                fos.write(headerLine.getBytes());
                for (final String allele : alleles) {
                    final StringBuilder lineBuilder = new StringBuilder();
                    lineBuilder.append("\"").append(allele).append("\"");
                    boolean writeLine = false;
                    for (final String marker : markers) {
                        lineBuilder.append(",");
                        final Float originMarkerAlleleFrequency = allFrequencies.get(origin + "/" + marker + "/" + allele);
                        if (originMarkerAlleleFrequency != null) {
                            lineBuilder.append("\"").append(originMarkerAlleleFrequency).append("\"");
                            writeLine = true;
                        }
                    }
                    if (writeLine) {
                        lineBuilder.append("\n");
                        fos.write(lineBuilder.toString().getBytes());
                    }
                }
            }
            finally {
                fos.close();
            }

            try {
                fos = new FileOutputStream(new File(strbaseFile.getParentFile(), strbaseFile.getName().replace(".xml", "_" + origin + "_Rare.txt")), false);
                fos.write(("Sample sizes and rare allele frequencies for " + origin + "\n").getBytes());
                for (final String marker : markers) {
                    final BigInteger size = sampleSizes.get(origin + "/" + marker);
                    if (size != null) {
                        fos.write(String.format("%-10s % 5d %g\n", marker, size.intValue(), 1.0 / (2 * size.intValue())).getBytes());
                    }
                }
            }
            finally {
                fos.close();
            }
        }
    }
}
