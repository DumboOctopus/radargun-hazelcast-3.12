package org.radargun.stages.cache.test;

import java.util.Random;

import org.radargun.config.DefinitionElement;
import org.radargun.config.Namespace;
import org.radargun.config.Property;
import org.radargun.config.PropertyHelper;
import org.radargun.stages.test.TestStage;

/**
 * @author Jakub Markos &lt;jmarkos@redhat.com&gt;
 */
public class GaussianKeysSelector implements KeySelector {
   private final Random random;
   private final long numEntries;
   private final long mean;
   private final long standardDeviation;
   private final long minimum, maximum;

   public GaussianKeysSelector(Random random, long numEntries, long mean, long standardDeviation, long minimum, long maximum) {
      this.random = random;
      this.numEntries = numEntries;
      this.mean = mean;
      this.standardDeviation = standardDeviation;
      this.minimum = minimum;
      this.maximum = maximum;
   }

   @Override
   public long next() {
      long result;
      
      result = (long) (random.nextGaussian() * standardDeviation + mean);
      while(result < minimum ||  maximum < result) {
         result = (long) (random.nextGaussian() * standardDeviation + mean);
      }

      return result;
   }

   @Namespace(name = TestStage.NAMESPACE, deprecatedName = TestStage.DEPRECATED_NAMESPACE)
   @DefinitionElement(name = "gaussian-keys", doc = "In the test fixed set of entries is used and this is shared among all stressor threads. Additionally, the keys" +
      "are selected using a normal (gaussian) distribution.")
   public static class Factory implements KeySelectorFactory {
      @Property(doc = "Total number of key-value entries.", optional = false)
      private long numEntries = 0;

      @Property(doc = "Mean value of indices. Default is numEntries/2.")
      private long mean = -1;

      @Property(doc = "Standard deviation. Default is numEntries/8.")
      private long standardDeviation = -1;

      @Property(doc = "Minimum output. Default is mean-3*standard-deviation")
      private long minimum = -1;

      @Property(doc = "Maximum output. Default is mean+3*standard-deviation")
      private long maximum = -1;

      @Override
      public KeySelector newInstance(CacheOperationsTestStage stage, Random random, int globalThreadId, int threadId) {
         if (mean == -1) {
            mean = numEntries / 2;
         }
         if (standardDeviation == -1) {
            // over 99% of values are within 3 standard deviations http://en.wikipedia.org/wiki/68%E2%80%9395%E2%80%9399.7_rule
            standardDeviation = numEntries / 8;
         }
         // minimum == -1 means it wasn't set in the XML.
         if (minimum == -1) {
            minimum = mean-3*standardDeviation;
         }
         if(maximum == -1) {
            maximum = mean+3*standardDeviation;
         }

         if(standardDeviation <= 0) {
            throw new IllegalArgumentException("Standard deviation must be positive for cache:gaussian-keys");
         }
         if(maximum <= minimum) {
            throw new IllegalArgumentException("Property maximum must be strictly greater than minimum for cache:gaussian-keys");
         }

         return new GaussianKeysSelector(random, numEntries, mean, standardDeviation, minimum, maximum);
      }

      @Override
      public String toString() {
         return GaussianKeysSelector.class.getSimpleName() + PropertyHelper.toString(this);
      }
   }
}
