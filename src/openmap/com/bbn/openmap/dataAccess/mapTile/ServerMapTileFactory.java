//**********************************************************************
//
//<copyright>
//
//BBN Technologies
//10 Moulton Street
//Cambridge, MA 02138
//(617) 873-8000
//
//Copyright (C) BBNT Solutions LLC. All rights reserved.
//
//</copyright>
//**********************************************************************
//
//$Source:
///cvs/darwars/ambush/aar/src/com/bbn/ambush/mission/MissionHandler.java,v
//$
//$RCSfile: MissionHandler.java,v $
//$Revision: 1.10 $
//$Date: 2004/10/21 20:08:31 $
//$Author: dietrick $
//
//**********************************************************************

package com.bbn.openmap.dataAccess.mapTile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;

import javax.swing.ImageIcon;

import com.bbn.openmap.Environment;
import com.bbn.openmap.I18n;
import com.bbn.openmap.PropertyConsumer;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.proj.Projection;
import com.bbn.openmap.util.FileUtils;
import com.bbn.openmap.util.PropUtils;
import com.bbn.openmap.util.cacheHandler.CacheObject;

/**
 * The ServerMapTileFactory is an extension to the StandardMapTileFactory that
 * can go to a http server to retrieve image tiles. You provide it with a root
 * URL that points to the parent directory of the tiles, and then this component
 * will add on the zoom/x/y.extension to that directory path to make the call
 * for a specific tile. Please make sure you have the permission of the server's
 * owner before hammering away at retrieving tiles from it.
 * 
 * This component can be configured using properties:
 * <p>
 * 
 * <pre>
 * # Inherited from StandardMapTileFactory
 * rootDir=the URL to the parent directory of the tiles on a server. The factory will construct specific file paths that are appended to this value. 
 * fileExt=the file extension to append to the tile names, should have a period.
 * cacheSize=the number of mapTiles the factory should hold on to. The default is 100.
 * 
 * # Additional properties
 * localCacheRootDir=if specified, the factory will store tiles locally at this root directory.  This directory is checked before going to the server, too.
 * </pre>
 * 
 * @author dietrick
 */
public class ServerMapTileFactory extends StandardMapTileFactory implements MapTileFactory,
        PropertyConsumer {
    public final static String LOCAL_CACHE_ROOT_DIR_PROPERTY = "localCacheRootDir";
    private static final int TIMEOUT_DEFAULT = 5000;
    /** Timeout property */
    public final static String TIMEOUT_PROPERTY = "timeout";

    protected String localCacheDir = null;
    private int timeout = TIMEOUT_DEFAULT;

    public ServerMapTileFactory() {
        this(null);
    }

    public ServerMapTileFactory(String rootDir) {
        this.rootDir = rootDir;
        this.fileExt = ".png";
    }

    /**
     * An auxiliary call to retrieve something from the cache, modified to allow
     * load method to do some projection calculations to initialize tile
     * parameters. If the object is not found in the cache, null is returned.
     */
    public Object getFromCache(Object key, int x, int y, int zoomLevel) {
        String localLoc = null;

        if (localCacheDir != null && zoomLevelInfo != null) {
            localLoc = buildLocalFilePath(x, y, zoomLevel, fileExt);
            /**
             * If a local cache is defined, then the cache will always use the
             * string for the local file as the key.
             */
            CacheObject ret = searchCache(localLoc);
            if (ret != null) {
                if (isVerbose()) {
                    logMessage("found tile (" + x + ", " + y + ") in cache");
                }
                return ret.obj;
            }
            /**
             * Return null if the localized version isn't found in cache when
             * local version is defined.
             */
            return null;
        }

        // Assuming that the localCacheDir is not defined, so the cache objects
        // will be using the server location as key

        CacheObject ret = searchCache(key);
        if (ret != null) {
            if (isVerbose()) {
                logMessage("found tile (" + x + ", " + y + ") in cache");
            }
            return ret.obj;
        }

        return null;
    }

    /**
     * Checks the local directory first for a locally cached version of the tile
     * before going off to the server. If a local directory is listed as a
     * cache, any retrieved files will be stored there for future use. We are
     * using the local name of the file as the cache key for all tiles for
     * consistency - all tiles are looked up with local cache locations.
     */
    public CacheObject load(Object key, int x, int y, int zoomLevel, Projection proj) {
        if (key instanceof String) {

            logMessage("fetching file for cache: " + key);

            byte[] imageBytes = null;

            CacheObject localVersion = super.load(key, x, y, zoomLevel, proj);

            if (localVersion != null) {
                logMessage("found version of tile in local cache: " + key);
                return localVersion;
            }

            // build file path here uses rootDir, which is the URL.
            String imagePath = buildFilePath(x, y, zoomLevel, fileExt);

            imageBytes = getImageBytes(imagePath, (String) key);

            if (imageBytes != null && imageBytes.length > 0) {
                // image found
                ImageIcon ii = new ImageIcon(imageBytes);

                try {
                    BufferedImage rasterImage = preprocessImage(ii.getImage(), ii.getIconWidth(), ii.getIconHeight());
                    OMGraphic raster = createOMGraphicFromBufferedImage(rasterImage, x, y, zoomLevel, proj);

                    /*
                     * Again, create a CacheObject based on the local name if
                     * the local dir is defined.
                     */
                    if (raster != null) {
                        return new CacheObject(key, raster);
                    }

                } catch (InterruptedException ie) {
                    if (isVerbose()) {
                        logMessage("factory interrupted fetching " + imagePath);
                    }
                }

            }

            /*
             * At this point, nothing was found for this location, so it's an
             * empty tile.
             */
            return getEmptyTile(key, x, y, zoomLevel, proj);
        }

        return null;
    }

    /**
     * Tries to get the image bytes from imagePath URL. If image found, will
     * write it locally to localFilePath for caching.
     * 
     * @param imagePath the source URL image path.
     * @param localFilePath the caching local file path
     * @return byte[] of image
     */
    public byte[] getImageBytes(String imagePath, String localFilePath) {
        InputStream in = null;
        byte[] imageBytes = null;
        try {
            java.net.URL url = new java.net.URL(imagePath);
            java.net.URLConnection urlc = url.openConnection();
            urlc.setConnectTimeout(timeout);
            final String contentType = urlc.getContentType();
            if (contentType != null && contentType.startsWith("image")) {
                in = urlc.getInputStream();
                imageBytes = FileUtils.readNBytes(in, Integer.MAX_VALUE, urlc.getContentLength());
                if (isVerbose()) {
                    logMessage("getImageBytes(" + imagePath + "): " + localFilePath);
                }
                if (localFilePath != null) {
                    File localFile = new File(localFilePath);
                    File parentDir = localFile.getParentFile();
                    parentDir.mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(localFile)) {
                        fos.write(imageBytes);
                    } catch (java.io.IOException ex) {
                        logMessage(Level.WARNING, "I/O Errror writing data for URL ("
                                + imagePath + "): " + ex.getLocalizedMessage());
                    }
            }
            } else {
                logMessage(Level.WARNING, "URL (" + imagePath
                        + ") has unexpected content type: " + contentType);
            }
        } catch (java.net.MalformedURLException murle) {
            logMessage(Level.WARNING, "malformed URL (" + imagePath + ")");
        } catch (java.io.IOException ex) {
            logMessage(Level.WARNING, "I/O Errror with URL (" + imagePath
                    + "): "
                    + ex.getLocalizedMessage());
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ex) {
                }
            }
        }
        return imageBytes;
    }

    @Override
    public String getLogPrefix() {
        return "ServerMapTileFactory: ";
    }

    /**
     * Acts the same as the buildFilePath method, but works for a local
     * directory specified in the properties.
     * 
     * @param x tile coordinate
     * @param y tile coordinate
     * @param z zoom level
     * @param fileExt file extension for image tiles.
     * @return new path for tile file
     */
    public String buildLocalFilePath(int x, int y, int z, String fileExt) {
        if (localTilePathBuilder == null) {
            localTilePathBuilder = new TilePathBuilder(localCacheDir);
        }

        return localTilePathBuilder.buildTilePath(x, y, z, fileExt);
    }

    private TilePathBuilder localTilePathBuilder = null;

    /**
     * Creates a unique cache key for this tile based on zoom, x, y. This method
     * was created so the ServerMapTileFactory could override it and use local
     * cache names for keys if a local cache was being used.
     * 
     * @param x tile coord.
     * @param y tile coord.
     * @param z zoomLevel.
     * @param fileExt file extension.
     * @return String used in cache.
     */
    protected String buildCacheKey(int x, int y, int z, String fileExt) {
        if (localCacheDir != null) {
            return buildLocalFilePath(x, y, z, fileExt);
        }
        return super.buildCacheKey(x, y, z, fileExt);
    }

    public Properties getProperties(Properties getList) {
        getList = super.getProperties(getList);
        getList.put(prefix + LOCAL_CACHE_ROOT_DIR_PROPERTY, PropUtils.unnull(localCacheDir));
        if (timeout != TIMEOUT_DEFAULT) {
            getList.setProperty(prefix + TIMEOUT_PROPERTY, String.valueOf(timeout));
        }
        return getList;
    }

    public Properties getPropertyInfo(Properties list) {
        list = super.getPropertyInfo(list);
        I18n i18n = Environment.getI18n();
        PropUtils.setI18NPropertyInfo(i18n, list, com.bbn.openmap.dataAccess.mapTile.StandardMapTileFactory.class, LOCAL_CACHE_ROOT_DIR_PROPERTY, "Local Cache Tile Directory", "Root directory containing image tiles retrieved from image server.", "com.bbn.openmap.util.propertyEditor.DirectoryPropertyEditor");
        list.setProperty(TIMEOUT_PROPERTY, "Timeout in milliseconds");
        return list;
    }

    public void setProperties(String prefix, Properties setList) {
        super.setProperties(prefix, setList);
        prefix = PropUtils.getScopedPropertyPrefix(prefix);

        localCacheDir = setList.getProperty(prefix + LOCAL_CACHE_ROOT_DIR_PROPERTY, localCacheDir);
        timeout = PropUtils.intFromProperties(setList, prefix
                + TIMEOUT_PROPERTY, TIMEOUT_DEFAULT);
    }

    /**
     * Tell the factory to dump the cache. For the ServerMapTileFactory, this
     * also includes the local file cache dir.
     */
    public void reset() {
        super.reset();
        if (localCacheDir != null) {
            File localCacheDirFile = new File(localCacheDir);
            if (localCacheDirFile.exists()) {
                try {
                    FileUtils.deleteFile(localCacheDirFile);
                    if (isVerbose()) {
                        logMessage("deleted local cache directory: "
                                + localCacheDirFile.toString());
                    }
                } catch (IOException e) {
                    logMessage("There's a problem deleting local cache directory: "
                            + e.getMessage());
                }
            }
        }
    }

}