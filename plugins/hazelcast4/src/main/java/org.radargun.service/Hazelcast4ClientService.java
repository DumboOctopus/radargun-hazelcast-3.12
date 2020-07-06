package org.radargun.service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.config.IndexType;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.radargun.Service;
import org.radargun.config.DefinitionElement;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.ProvidesTrait;
import org.radargun.utils.AddressStringListConverter;


/**
 * Hazelcast client service.
 * <p>
 * Unfortunately Hazelcast4 puts IMap in the map package while previous versions put it
 * in the map package.
 *
 * @author Roman Macor &lt;rmacor@redhat.com&gt;
 */

@Service(doc = "Hazelcast 4 client")
public class Hazelcast4ClientService implements Lifecycle { //extends Hazelcast37ClientService  {

   protected HazelcastInstance hazelcastInstance;

   @Property(doc = "List of server addresses the clients should connect to, separated by semicolons (;).", converter = AddressStringListConverter.class)
   protected String[] servers;

   @Property(doc = "Group name, the default is dev")
   protected String groupName = "";

   @Property(name = "cache", doc = "Name of the map ~ cache", deprecatedName = "map")
   protected String mapName = "default";

   @Property(name = Service.FILE, doc = "File used as a configuration for this service.", deprecatedName = "config")
   protected String configFile;

   @Property(doc = "Indices that should be build.", complexConverter = Hazelcast36Service.IndexConverter.class)
   protected List<Hazelcast4ClientService.Index> indices = Collections.EMPTY_LIST;

   protected final Log log = LogFactory.getLog(getClass());

   @Override
   public void start() {
      ClientConfig clientConfig = new ClientConfig();
      //clientConfig.setProperty("hazelcast.operation.call.timeout.millis", "12000");

      // we will use config file if its specified.
      // otherwise, we will use defaults above
      if (configFile != null) {
         try {
            clientConfig = new XmlClientConfigBuilder(configFile).build();
         } catch (IOException e) {
            log.error("Failed to get configuration", e);
         }
      }

      // the radargun configuration file overrides stuff in configFile
      // this is complicated because hazelcast default name is dev
      // radargun's default is ""
      // so if:
      //    unset in radargun & unset in client => dev (hazelcast default)
      //    unset in radargun & set in client => clientconfig
      //    set in radargun & unset in client => radargun
      //    set in radargun & set in client => radargun
      // same idea for servers

      if(!groupName.equals(""))
         clientConfig.setClusterName(groupName);

      if(servers != null)
         clientConfig.getNetworkConfig().addAddress(servers);

      hazelcastInstance = HazelcastClient.newHazelcastClient(clientConfig);
   }

   @Override
   public void stop() {
      hazelcastInstance.getLifecycleService().shutdown();
      hazelcastInstance = null;
   }

   @Override
   public boolean isRunning() {
      return hazelcastInstance != null && hazelcastInstance.getLifecycleService().isRunning();
   }

   @ProvidesTrait
   public Hazelcast4ClientOperations createOperations() {
      return new Hazelcast4ClientOperations(this);
   }


   @ProvidesTrait
   public Hazelcast4ClientService getSelf() {
      return this;
   }

   @DefinitionElement(name = "index", doc = "Index definition.")
   protected static class Index {
      @Property(doc = "Map on which the index should be built. Default is the default map.")
      protected String mapName;

      @Property(doc = "Should be the index ordered? Default is true")
      protected boolean ordered = true;

      @Property(doc = "Path in the indexed object.", optional = false)
      protected String path;

      // whether we have registered this index
      private boolean added = false;
   }

   protected <K, V> IMap<K, V> getMap(String mapName) {
      if (mapName == null) {
         mapName = this.mapName;
      }
      IMap<K, V> map = hazelcastInstance.getMap(mapName);

      for (Hazelcast4ClientService.Index index : indices) {
         synchronized (index) {
            if (index.added) {
               continue;
            }
            if ((index.mapName == null && map.getName().equals(this.mapName))
               || (index.mapName != null && map.getName().equals(index.mapName))) {
               IndexType indexType = IndexType.HASH;
               if (index.ordered)
                  indexType = IndexType.SORTED;
               map.addIndex(indexType, index.path);
               index.added = true;
            }
         }
      }
      return map;
   }

    @ProvidesTrait
    public Hazelcast4Queryable createQueryable() {
       return new Hazelcast4Queryable(this);
    }

}
