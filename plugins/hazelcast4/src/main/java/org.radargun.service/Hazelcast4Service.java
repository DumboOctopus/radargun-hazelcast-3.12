package org.radargun.service;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.util.*;

import com.hazelcast.cluster.MembershipEvent;
import com.hazelcast.cluster.MembershipListener;
import com.hazelcast.config.Config;
import com.hazelcast.config.IndexType;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.radargun.Service;
import org.radargun.config.DefinitionElement;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Clustered;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.ProvidesTrait;
import org.radargun.utils.ReflexiveConverters;


/**
 * An implementation of CacheWrapper that uses Hazelcast instance as an underlying implementation.
 * Unfortunately, since the import paths are different in hazelcast 4, the moment
 * that Java tries to load Hazecast36Service it will crash. So that is why we cannot
 * extend Hazelcast36Service and we must copy paste instead :(
 *
 * @author Maido Kaara
 */
@Service(doc = HazelcastService.SERVICE_DESCRIPTION)
public class Hazelcast4Service implements Lifecycle, Clustered {

   // from HazelcastService
   protected static final String SERVICE_DESCRIPTION = "Hazelcast 4";

   protected final Log log = LogFactory.getLog(getClass());
   private final boolean trace = log.isTraceEnabled();

   // begin From Hazelcast36Service
   @Property(doc = "Indices that should be build.", complexConverter = IndexConverter.class)
   protected List<Index> indices = Collections.EMPTY_LIST;
   // end

   protected HazelcastInstance hazelcastInstance;
   protected List<Membership> membershipHistory = new ArrayList<>();

   @Property(name = Service.FILE, doc = "Configuration file.")
   protected String config;

   @Property(name = "cache", doc = "Name of the map ~ cache", deprecatedName = "map")
   protected String mapName = "default";

   // @Property(name = "useTransactions", doc = "Whether the service should use transactions. Default is false.")
   // protected boolean useTransactions = false;

   @ProvidesTrait
   public Hazelcast4Service getSelf() {
      return this;
   }

   // @ProvidesTrait
   // public Transactional createTransactional() {
   //    return new HazelcastTransactional(this);
   // }

   @ProvidesTrait
   public Hazelcast4CacheInfo createCacheInfo() {
      return new Hazelcast4CacheInfo(this);
   }

   @ProvidesTrait
   public Hazelcast4ConfigurationProvider createConfigurationProvider() {
      return new Hazelcast4ConfigurationProvider(this);
   }

   @ProvidesTrait
   public Hazelcast4Operations createOperations() {
      return new Hazelcast4Operations(this);
   }

//   @ProvidesTrait
//   public Hazelcast4Queryable createQueryable() {
//      return new Hazelcast4Queryable(this);
//   }

//   @ProvidesTrait
//   public HazelcastContinuousQuery createContinuousQuery() {
//      return new HazelcastContinuousQuery(this);
//   }

   @Override
   public void start() {
      log.info("Creating cache with the following configuration: " + config);
      try (InputStream configStream = getAsInputStreamFromClassLoader(config)) {
         Config cfg = new XmlConfigBuilder(configStream).build();
         hazelcastInstance = Hazelcast.newHazelcastInstance(cfg);
         MembershipListener listener = new MembershipListener() {
            @Override
            public void memberAdded(MembershipEvent membershipEvent) {
               updateMembers(membershipEvent.getMembers());
            }

            @Override
            public void memberRemoved(MembershipEvent membershipEvent) {
               updateMembers(membershipEvent.getMembers());
            }
         };
         synchronized (this) {
            addMembershipListener(listener);
            updateMembers(hazelcastInstance.getCluster().getMembers());
         }
         log.info("Hazelcast configuration:" + hazelcastInstance.getConfig().toString());
      } catch (IOException e) {
         log.error("Failed to get configuration input stream", e);
      }
   }

   protected void addMembershipListener(MembershipListener listener) {
      hazelcastInstance.getCluster().addMembershipListener(listener);
   }

   @Override
   public void stop() {
      hazelcastInstance.getLifecycleService().shutdown();
      updateMembers(Collections.EMPTY_SET);
      hazelcastInstance = null;
   }

   @Override
   public boolean isRunning() {
      return hazelcastInstance != null && hazelcastInstance.getLifecycleService().isRunning();
   }

   @Override
   public boolean isCoordinator() {
      return false;
   }

   @Override
   public synchronized Collection<Member> getMembers() {
      if (membershipHistory.isEmpty()) return null;
      return membershipHistory.get(membershipHistory.size() - 1).members;
   }

   @Override
   public synchronized List<Membership> getMembershipHistory() {
      return new ArrayList<>(membershipHistory);
   }

   protected synchronized void updateMembers(Set<com.hazelcast.cluster.Member> members) {
      ArrayList<Member> mbrs = new ArrayList<>(members.size());
      for (com.hazelcast.cluster.Member m : members) {
         mbrs.add(new Member(m.getSocketAddress().getHostName() + "(" + m.getUuid() + ")", m.localMember(), false));
      }
      membershipHistory.add(Membership.create(mbrs));
   }

   private InputStream getAsInputStreamFromClassLoader(String filename) {
      InputStream is = null;
      ClassLoader cl = getClass().getClassLoader();
      if (cl != null) {
         is = cl.getResourceAsStream(filename);
      }
      if (is == null) {
         try {
            return new FileInputStream(FileSystems.getDefault().getPath(filename).toFile());
         } catch (FileNotFoundException e) {
            is = null;
         }
      }
      return is;
   }


   // From Hazelcast36Service


   protected <K, V> IMap<K, V> getMap(String mapName) {
      if (mapName == null) {
         mapName = this.mapName;
      }
      IMap<K, V> map = hazelcastInstance.getMap(mapName);
      for (Index index : indices) {
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

   protected static class IndexConverter extends ReflexiveConverters.ListConverter {
      public IndexConverter() {
         super(new Class[]{Index.class});
      }
   }
}
