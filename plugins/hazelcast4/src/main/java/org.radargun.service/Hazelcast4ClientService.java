package org.radargun.service;

import org.radargun.Service;
import org.radargun.traits.ProvidesTrait;

/**
 * Hazelcast client service
 *
 * @author Roman Macor &lt;rmacor@redhat.com&gt;
 */

@Service(doc = "Hazelcast client")
public class Hazelcast4ClientService extends Hazelcast312ClientService {


   @ProvidesTrait
   @Override
   public Hazelcast4ClientService getSelf() {
      return this;
   } 


}
