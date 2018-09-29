package jpl.mipl.io.vicar.test;

import jpl.mipl.io.vicar.*;
import org.junit.Test;

import java.awt.*;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static org.junit.Assert.assertTrue;

/**
 * Test program for VICAR I/O writes.  Simulates much of the functionality of
 * the VICAR program "gen".  No attempt is made to do "nice" argument parsing.
 */

public class TestGen {
    String _outfile;
    int _nl;
    int _ns;
    int _nb;
    String _datatype;
    int _datatype_code;
    String _org;
    String _method;
    double _linc;
    double _sinc;
    double _binc;
    int _tileHeight;
    int _tileWidth;
    int _pixelStride;
    String _infile = null;


    /***********************************************************************
     */
    public static String callLabel(String fileName, String[] flags) throws Exception {
        StringBuffer flagBuffer = new StringBuffer();
        for (String flag : flags) {
            flagBuffer.append("-" + flag);
        }

        String command = "./p2/lib/x86-macosx/label -list ./" + fileName + " " + flagBuffer.toString();
        return callExternalCommand(command);
    }

    public static String callGen(String fileName, String args) throws Exception {
        String command = "./p2/lib/x86-macosx/gen " + fileName + " " + args;
        return callExternalCommand(command);
    }

    public static String callDifPic(String firstFile, String secondFile) throws Exception {
        String command = "./p2/lib/x86-macosx/difpic (" + firstFile + " " + secondFile + ")";
        return callExternalCommand(command);
    }


    @Test
    public void test1() throws Exception {
        String fileName = "a";
        String[] args = (fileName + " 100 100 3 byte bsq line 2 3 5 12 21 1").split(" ");
        fileGeneration(args);

        String labelResult = callLabel(fileName, new String[0]);
        //System.out.println("output of label: " + labelResult);

        assertTrue(labelResult.contains("************  File ./" + fileName + " ************"));
        assertTrue(labelResult.contains("3 dimensional IMAGE file"));
        assertTrue(labelResult.contains("File organization is BSQ"));
        assertTrue(labelResult.contains("Pixels are in BYTE format from a JAVA host"));
        assertTrue(labelResult.contains("3 bands"));
        assertTrue(labelResult.contains("100 lines per band"));
        assertTrue(labelResult.contains("100 samples per line"));
        assertTrue(labelResult.contains("0 lines of binary header"));
        assertTrue(labelResult.contains("0 bytes of binary prefix per line"));
        assertTrue(labelResult.contains("IVAL=0.0"));
        assertTrue(labelResult.contains("SINC=3.0"));
        assertTrue(labelResult.contains("LINC=2.0"));
        assertTrue(labelResult.contains("BINC=5.0"));
        assertTrue(labelResult.contains("MODULO=0.0"));

        labelResult = callLabel(fileName, new String[]{"dump"});
        System.out.println("output of label with dump: " + labelResult);

        String secondFileName = "b";
        String genResult = callGen(secondFileName, "100 100 3 -byte -bsq linc=2 sinc=3 binc=5");
        System.out.println("output of gen b: " + genResult);
    }

    public void fileGeneration(String argv[]) throws Exception {
        if (argv.length != 12 && argv.length != 13) {
            System.out.println("Usage:");
//						0       1  2  3     4      5     6    7    8    9      10         11        12          13
            System.out.println("java TestGen outfile(s) NL NS NB datatype org method LINC SINC BINC tileHeight tileWidth pixelStride [infile]");
            System.out.println("  where NL, NS, NB, LINC, SINC, BINC are as in gen");
            System.out.println("  outfile(s) is a single string with 1 or more comma-sep filenames (a la xvd)");
            System.out.println("  datatype is byte, half, full, real, or double (complex not supported)");
            System.out.println("  org is bsq, bil, or bip");
            System.out.println("  method is line or tile to indicate the writing method");
            System.out.println("  tileHeight and tileWidth are always required but used only for method=tile");
            System.out.println("  pixelStride tests that aspect of line I/O (ignored for tiles");
            System.out.println("  and infile is an optional input file; used only to test copying label from");
            System.out.println("    primary input");
            System.out.println("No checking is done of arguments (it's a test program); bad values will crash.");

            System.exit(0);
        }

        // Parse arguments

        _outfile = argv[0];
        _nl = Integer.parseInt(argv[1]);
        _ns = Integer.parseInt(argv[2]);
        _nb = Integer.parseInt(argv[3]);
        _datatype = argv[4];
        _org = argv[5];
        _method = argv[6];
        _linc = Double.parseDouble(argv[7]);
        _sinc = Double.parseDouble(argv[8]);
        _binc = Double.parseDouble(argv[9]);
        _tileHeight = Integer.parseInt(argv[10]);
        _tileWidth = Integer.parseInt(argv[11]);
        _pixelStride = Integer.parseInt(argv[12]);
        if (argv.length == 14)
            _infile = argv[13];

        // Set up and create the output file

        VicarOutputFile voif = new VicarOutputFile();

        if (_infile != null) {            // set primary input
            voif.setPrimaryInput(new VicarInputImage(_infile));
        }

        SystemLabel sys = voif.getSystemLabel();
        sys.setOrg(_org);
        sys.setNL(_nl);
        sys.setNS(_ns);
        sys.setNB(_nb);
        sys.setFormat(_datatype);
        _datatype_code = sys.getFormatCode();

        voif.setSystemLabel(sys);

        voif.open(_outfile);

        // Update the label, stuff it with things like GEN does

        VicarLabel lbl = voif.getVicarLabel();
        VicarLabelSet task = lbl.createHistoryTask("TestGen");
        task.add(new VicarLabelItem("IVAL", 0.0f));
        task.add(new VicarLabelItem("SINC", _sinc));
        task.add(new VicarLabelItem("LINC", _linc));
        task.add(new VicarLabelItem("BINC", _binc));
        task.add(new VicarLabelItem("MODULO", 0.0f));

        voif.setVicarLabel(lbl);

        if (_method.equalsIgnoreCase("line")) {
            if (_org.equalsIgnoreCase("bip"))
                testLine(voif, _nl, _ns, _nb, _linc, _sinc, _binc, _pixelStride);
            else if (_org.equalsIgnoreCase("bil"))
                testLine(voif, _nl, _nb, _ns, _linc, _binc, _sinc, _pixelStride);
            else        // bsq
                testLine(voif, _nb, _nl, _ns, _binc, _linc, _sinc, _pixelStride);
        } else {                    // tile
            testTile(voif);
        }

        voif.close();
    }

    /***********************************************************************
     * Test via record (line) oriented writes.  The array is allocated
     * n1*pixelStride and every stride'th pixel is set.  The write should only
     * pick up those array elements.
     */
    public void testLine(VicarOutput vo, int n3, int n2, int n1,
                         double inc3, double inc2, double inc1, int pixelStride)
            throws IOException {
        switch (_datatype_code) {
            case SystemLabel.TYPE_BYTE:

                byte[] bbuf = new byte[n1 * pixelStride];
                for (int i3 = 0; i3 < n3; i3++) {
                    for (int i2 = 0; i2 < n2; i2++) {
                        for (int i1 = 0; i1 < n1; i1++) {
                            bbuf[i1 * pixelStride] =
                                    (byte) ((i3 * inc3) + (i2 * inc2) + (i1 * inc1));
                        }
                        vo.writeRecord(bbuf, 0, n1, 0, pixelStride, i2, i3);
                    }
                }
                break;

            case SystemLabel.TYPE_HALF:

                short[] sbuf = new short[n1 * pixelStride];
                for (int i3 = 0; i3 < n3; i3++) {
                    for (int i2 = 0; i2 < n2; i2++) {
                        for (int i1 = 0; i1 < n1; i1++) {
                            sbuf[i1 * pixelStride] =
                                    (short) ((i3 * inc3) + (i2 * inc2) + (i1 * inc1));
                        }
                        vo.writeRecord(sbuf, 0, n1, 0, pixelStride, i2, i3);
                    }
                }
                break;

            case SystemLabel.TYPE_FULL:

                int[] ibuf = new int[n1 * pixelStride];
                for (int i3 = 0; i3 < n3; i3++) {
                    for (int i2 = 0; i2 < n2; i2++) {
                        for (int i1 = 0; i1 < n1; i1++) {
                            ibuf[i1 * pixelStride] =
                                    (int) ((i3 * inc3) + (i2 * inc2) + (i1 * inc1));
                        }
                        vo.writeRecord(ibuf, 0, n1, 0, pixelStride, i2, i3);
                    }
                }
                break;

            case SystemLabel.TYPE_REAL:

                float[] fbuf = new float[n1 * pixelStride];
                for (int i3 = 0; i3 < n3; i3++) {
                    for (int i2 = 0; i2 < n2; i2++) {
                        for (int i1 = 0; i1 < n1; i1++) {
                            fbuf[i1 * pixelStride] =
                                    (float) ((i3 * inc3) + (i2 * inc2) + (i1 * inc1));
                        }
                        vo.writeRecord(fbuf, 0, n1, 0, pixelStride, i2, i3);
                    }
                }
                break;

            case SystemLabel.TYPE_DOUB:

                double[] dbuf = new double[n1 * pixelStride];
                for (int i3 = 0; i3 < n3; i3++) {
                    for (int i2 = 0; i2 < n2; i2++) {
                        for (int i1 = 0; i1 < n1; i1++) {
                            dbuf[i1 * pixelStride] =
                                    (double) ((i3 * inc3) + (i2 * inc2) + (i1 * inc1));
                        }
                        vo.writeRecord(dbuf, 0, n1, 0, pixelStride, i2, i3);
                    }
                }
                break;

        }
    }

    /***********************************************************************
     * Test tile-oriented writes.  Note that ORG doesn't matter here; it's
     * de-scrambled by the vicar package itself.
     */

    public void testTile(VicarOutput vo) throws IOException {
        // Write tiles top-to-bottom first in order to test random access

        int ntile_x = (_ns - 1) / _tileWidth + 1;
        int ntile_y = (_nl - 1) / _tileHeight + 1;

        for (int y_tile = 0; y_tile < ntile_y; y_tile++) {
            for (int x_tile = 0; x_tile < ntile_x; x_tile++) {
                Raster r = createTile(x_tile, y_tile, vo);
                vo.writeTile(r.getMinX(), r.getMinY(), r.getSampleModel(),
                        r.getDataBuffer());
            }
        }
    }

    /***********************************************************************
     * Create a tile given tile indices.
     */
    public Raster createTile(int x_tile, int y_tile, VicarOutput vo) {
        SampleModel sm = vo.createSampleModel(_tileWidth, _tileHeight);
        DataBuffer db = sm.createDataBuffer();

        int x = x_tile * _tileWidth;
        int y = y_tile * _tileHeight;

        for (int line = 0; line < _tileHeight; line++) {
            for (int samp = 0; samp < _tileWidth; samp++) {
                for (int band = 0; band < _nb; band++) {
                    sm.setSample(samp, line, band,
                            ((band * _binc) + (line + y) * _linc + (samp + x) * _sinc), db);
                }
            }
        }
        return Raster.createRaster(sm, db, new Point(x, y));
    }


    /**
     * Executes the provided String as an OS command.
     *
     * @param command
     * @return
     * @throws Exception
     */
    public static String callExternalCommand(String command) throws Exception {
        InputStream pwdInputStream = Runtime.getRuntime().exec(command).getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(pwdInputStream));

        String line = "";
        StringBuffer out = new StringBuffer();
        while ((line = reader.readLine()) != null) {
            out.append(line).append("\n");
        }

        return out.toString();
    }

}

