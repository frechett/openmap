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

import java.util.logging.Level;
import java.util.logging.Logger;

import com.bbn.openmap.omGraphics.OMGraphicList;
import com.bbn.openmap.proj.Projection;

/**
 * An object that fetches tiles for a given projection. It could cache the
 * tiles, it can get them from anywhere it might want to.
 * 
 * @author dietrick
 */
public interface MapTileFactory {
	/**
	 * Create an OMGraphicList with a set of tiles on it.
	 * 
	 * @param proj
	 * @return OMGraphicList that was created.
	 */
	OMGraphicList getTiles(Projection proj);

	/**
	 * Create an OMGraphicList that covers the projection with tiles that suit
	 * the specified zoom level.
	 */
	OMGraphicList getTiles(Projection proj, int zoomLevel);

	/**
	 * Add tiles to OMGraphicList provided that suit the given projection.
	 * 
	 * @param proj
	 * @param list
	 * @return the OMGraphicList provided.
	 */
	OMGraphicList getTiles(Projection proj, int zoomLevel, OMGraphicList list);

	/**
	 * Set a MapTileRequestor in the tile factory that should be told to repaint
	 * when new tiles become available, and to check with during the tile fetch
	 * whether to keep going or not. listUpdate will be called when a new tile
	 * has been added to the OMGraphicList passed in the getTiles method, and
	 * shouldContinue will be called during stable times during the getTiles
	 * fetch.
	 * 
	 * @param requestor
	 *            callback MapTileRequestor to ask status questions.
	 */
	void setMapTileRequester(MapTileRequester requestor);

	/**
	 * Tell the factory to clean up resources.
	 */
	void reset();

    /**
     * Get object that handles empty tiles.
     * 
     * @return EmptyTileHandler used by the factory.
     */
    EmptyTileHandler getEmptyTileHandler();

    /**
     * Get the logger.
     * 
     * @return the logger.
     */
    Logger getLogger();

    /**
     * Get the log prefix.
     * 
     * @return the log prefix or null if none.
     */
    default String getLogPrefix() {
        return null;
    }

    /**
     * Get the verbose level.
     * 
     * @return the verbose level.
     */
    default Level getVerbose() {
        return Level.FINE;
    }

    /**
     * Determines if verbose logging is enabled.
     * 
     * @return true if verbose logging is enabled, false otherwise.
     */
    default boolean isVerbose() {
        return getLogger().isLoggable(getVerbose());
    }

    /**
     * Log the message.
     * 
     * @param level the level.
     * @param s the message.
     */
    default void logMessage(Level level, String s) {
        logMessage(level, s, null);
    }

    /**
     * Log the message.
     * 
     * @param level the level.
     * @param s the message or null if none.
     * @param ex the exception or null if none.
     */
    default void logMessage(Level level, String s, Exception ex) {
        String logPrefix = getLogPrefix();
        if (s == null || s.isEmpty()) {
            s = logPrefix;
        } else if (logPrefix != null && !logPrefix.isEmpty()) {
            s = logPrefix + s;
        }
        if (ex != null) {
            getLogger().log(level, s, ex);
        } else {
            getLogger().log(level, s);
        }
    }

    /**
     * Log message if verbose.
     * 
     * @param s the message.
     */
    default void logMessage(String s) {
        logMessage(getVerbose(), s, null);
    }

    /**
     * Log message if verbose.
     * 
     * @param s the message or null if none.
     * @param ex the exception or null if none.
     */
    default void logMessage(String s, Exception ex) {
        logMessage(getVerbose(), s, ex);
    }
}
