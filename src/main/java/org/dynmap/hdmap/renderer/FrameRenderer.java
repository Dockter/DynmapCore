package org.dynmap.hdmap.renderer;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

import org.dynmap.Log;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;
import org.dynmap.renderer.RenderPatchFactory.SideVisible;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.MapIterator;

public class FrameRenderer extends CustomRenderer {
    private static final int TEXTURE_SIDE = 0;
    private int blkid;
    // Map of block ID sets for linking blocks
    private static Map<String, BitSet> linked_ids_by_set = new HashMap<String, BitSet>();
    // Set of linked blocks
    private BitSet linked_ids;
    // Diameter of frame/wore (1.0 = full block)
    private double diameter;
    // Models for connection graph (bit0=X+,bit1=X-,bit2=Y+,bit3=Y-,bit4=Z+,bit5=Z-)
    private RenderPatch[][] models = new RenderPatch[64][];
    // Base index (based on force parameter)
    private int base_index = 0;
    
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String,String> custparm) {
        if(!super.initializeRenderer(rpf, blkid, blockdatamask, custparm))
            return false;
        this.blkid = blkid; /* Remember our block ID */
        String linkset = custparm.get("linkset");
        if(linkset == null) linkset = "default";
        linked_ids = linked_ids_by_set.get(linkset); /* Get our set */
        if(linked_ids == null) {
            linked_ids = new BitSet();
            linked_ids_by_set.put(linkset,  linked_ids);
        }
        linked_ids.set(blkid);  // Add us to set
        // Get diameter
        String dia = custparm.get("diameter");
        if(dia == null) dia = "0.5";
        try {
            diameter = Double.parseDouble(dia);
        } catch (NumberFormatException nfx) {
            diameter = 0.5;
            Log.severe("Error: diameter must be number between 0.0 and 1.0");
        }
        if((diameter <= 0.0) || (diameter >= 1.0)) {
            Log.severe("Error: diameter must be number between 0.0 and 1.0");
            diameter = 0.5;
        }
        // Process other link block IDs
        for(String k : custparm.keySet()) {
            if(k.startsWith("linkid")) {
                int linkblkid = 0;
                try {
                    linkblkid = Integer.parseInt(custparm.get(k));
                } catch (NumberFormatException nfx) {
                }
                if(linkblkid > 0) {
                    linked_ids.set(linkblkid);
                }
            }
        }
        // Check for axis force
        String force = custparm.get("force");
        if(force != null) {
            String v = "xXyYzZ";
            for(int i = 0; i < v.length(); i++) {
                if(force.indexOf(v.charAt(i)) >= 0) {   
                    base_index |= (1 << i);
                }
            }
        }
        /* Build models for various connection graphs */
        for(int i = 0; i < models.length; i++) {
            models[i] = buildModel(rpf, i);
        }
        return true;
    }

    @Override
    public int getMaximumTextureCount() {
        return 1;
    }
    
    private RenderPatch[] buildModel(RenderPatchFactory rpf, int idx) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        
        /* If we have an X axis match */
        if((idx & 0x3) != 0) {
            addBox(rpf, list, 
                ((idx & 1) != 0)?0.0:(0.5-diameter/2.0),
                ((idx & 2) != 0)?1.0:(0.5+diameter/2.0),
                (0.5 - diameter/2.0),
                (0.5 + diameter/2.0),
                (0.5 - diameter/2.0),
                (0.5 + diameter/2.0),
                null);
        }
        /* If we have an Y axis match */
        if((idx & 0xC) != 0) {
            addBox(rpf, list, 
                (0.5 - diameter/2.0),
                (0.5 + diameter/2.0),
                ((idx & 0x4) != 0)?0.0:(0.5-diameter/2.0),
                ((idx & 0x8) != 0)?1.0:(0.5+diameter/2.0),
                (0.5 - diameter/2.0),
                (0.5 + diameter/2.0),
                null);
        }
        /* If we have an Z axis match, or no links */
        if(((idx & 0x30) != 0) || (idx == 0)) {
            addBox(rpf, list, 
                (0.5 - diameter/2.0),
                (0.5 + diameter/2.0),
                (0.5 - diameter/2.0),
                (0.5 + diameter/2.0),
                ((idx & 0x10) != 0)?0.0:(0.5-diameter/2.0),
                ((idx & 0x20) != 0)?1.0:(0.5+diameter/2.0),
                null);
        }
        
        return list.toArray(new RenderPatch[list.size()]);
    }

    private static final int[] x_off = { -1, 1, 0, 0, 0, 0 };
    private static final int[] y_off = { 0, 0, -1, 1, 0, 0 };
    private static final int[] z_off = { 0, 0, 0, 0, -1, 1 };
    
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
        int idx = base_index;
        for(int i = 0; i < x_off.length; i++) {
            if((idx & (1 << i)) != 0) continue;
            int blkid = ctx.getBlockTypeIDAt(x_off[i],  y_off[i],  z_off[i]);
            if(linked_ids.get(blkid)) {
                idx |= (1 << i);
            }
        }
        return models[idx];
    }
}
