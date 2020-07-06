package org.radargun.service;


import org.radargun.config.Property;

//@DefinitionElement(name = "index", doc = "Index definition.")
public class Hazelcast4Index {
   @Property(doc = "Map on which the index should be built. Default is the default map.")
   protected String mapName;

   @Property(doc = "Should be the index ordered? Default is true")
   protected boolean ordered = true;

   @Property(doc = "Path in the indexed object.", optional = false)
   protected String path;

   // whether we have registered this index
   private boolean added = false;


   // public static <K, V> void updateIndicies(IMap<K,V> map, String mapName, List<Index> indices){
   //    for (Index index : indices) {
   //       synchronized (index) {
   //          if (index.added) {
   //             continue;
   //          }
   //          if ((index.mapName == null && map.getName().equals(mapName))
   //                  || (index.mapName != null && map.getName().equals(index.mapName))) {
   //             IndexType indexType = IndexType.HASH;
   //             if(index.ordered)
   //                indexType = IndexType.SORTED;
   //             map.addIndex(indexType, index.path);
   //             index.added = true;
   //          }
   //       }
   //    }
   // }
}