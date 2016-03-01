package example.auction;

import io.baratine.core.Lookup;
import io.baratine.core.ResourceManager;
import io.baratine.core.ResourceService;
import io.baratine.core.ServiceManager;
import io.baratine.core.ServiceRef;
import io.baratine.core.Services;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.inject.Inject;

import com.caucho.json.io.JsonWriter;

import example.arena.Arena;

@ResourceService("public:///query")
public class AuctionQueryServiceImpl implements AuctionQueryService
{ 
  //@Inject
  //@Lookup("public:///auction")
  //private ResourceManager _manager;
  
  @Override
  public Auction createAuction(String userId,
                               long durationMs, long startPrice,
                               String title, String description)
  {
    ResourceManager manager = getResourceManager();
    
    ServiceRef ref = manager.create(userId, durationMs, startPrice, title, description);
        
    Auction auction = ref.as(Auction.class);
    
    Auction copy = new Auction(auction);
              
    return copy;
  }
  
  @Override
  public Auction getAuction(long id)
  {
    String query = "_id = " + id;
    
    ServiceRef ref = getResourceManager().findOne(query);
    
    if (ref == null) {
      return null;
    }
    
    Auction auction = ref.as(Auction.class);
    
    return new Auction(auction);
  }
  
  @Override
  public List<Auction> getAuctions(int limit, boolean isDescending)
  {
    if (limit <= 0 || limit > 50) {
      limit = 50;
    }
        
    String query = "_id >= 0";
    
    if (isDescending) {
      query += " ORDER BY _id DESC";
    }
        
    return rawQuery(query);
  }
  
  @Override
  public List<Auction> getAuctionsByUser(String userId, int limit, boolean isDescending)
  {
    if (limit <= 0 || limit > 50) {
      limit = 50;
    }
        
    String query = "_id >= 0 && _userId = " + userId;
    
    if (isDescending) {
      query += " ORDER BY _id DESC";
    }
    
    return rawQuery(query);
  }
  
  public List<Auction> query(String text)
  {
    String query = "_title LIKE '" + text + "' OR _description LIKE '" + text + "'";
    
    return rawQuery(query);
  }
  
  @Override
  public List<Auction> rawQuery(String query)
  {
    List<Auction> list = new ArrayList<Auction>();

    Iterable<ServiceRef> serviceIter
      = getResourceManager().findAll(query);
        
    if (serviceIter != null) {
      Iterator<ServiceRef> iter = serviceIter.iterator();
                  
      while (iter.hasNext()) {
        Auction auction = iter.next().as(Auction.class);
                
        list.add(new Auction(auction));
      }
    }

    return list;
  }

  private ResourceManager getResourceManager()
  {
    String serviceName = "public:///auction";
    
    ServiceManager serviceManager = Services.getCurrentManager();

    ResourceManager resourceManager
      = serviceManager.lookup(serviceName).as(ResourceManager.class);
    
    return resourceManager;
  }
}
