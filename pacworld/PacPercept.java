package pacworld;

import agent.Percept;
import agent.Agent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/** A percept in the package delivery world. */
public class PacPercept extends Percept{

   protected boolean bump;
   protected ArrayList<VisibleAgent> visAgents;
   protected ArrayList<VisiblePackage> visPackages;
   protected String[] messages;
   protected VisiblePackage heldPackage;
   protected int worldSize;
   
   public static final int VIS_RADIUS = 5;           // allows 11x11 square of visibility
   public static double LOCATION_NOISE = 0.05;   // the chance that a visible package will be seen in the wrong spot
   public static double PHANTOM_PACKAGE_NOISE = 0.02;   // chance that an imaginary package will be seen for a percept
   public static double DESTINATION_NOISE = 0.20;         // chance that a visible package will appear to have the wrong destination

   /** Construct a package delivery world percept. The agents can
      see everything in 9x9 grid centered on it. If the agent moved into an
      obstacle on the previous turn the agent will feel a bump. */
   public PacPercept(PackageState state, Agent agent) {

      super(state,agent);

      worldSize = state.getMapSize();

      int ax = state.getAgentX(agent);
      int ay = state.getAgentY(agent);
      
      // find agents in view
      Collection<PacAgentRep> agentReps = state.getAgentReps();
      visAgents = new ArrayList<VisibleAgent>();
      Iterator<PacAgentRep> agtItr = agentReps.iterator();
      int tx, ty;
      while (agtItr.hasNext()) {
         PacAgentRep arep = agtItr.next();
         tx = arep.getX();
         ty = arep.getY();
         if (inRange(ax,ay,tx,ty)) {
            VisibleAgent vagent = new VisibleAgent(arep.getId(), arep.getX(), 
                                                   arep.getY());
            visAgents.add(vagent);
         }
      }

      // find packages in view
      List<Package> packages = state.getPackages();
      visPackages = new ArrayList<VisiblePackage>();
      for (int i=0; i < state.getOrigNumPackages(); i++) {
         if (packages.get(i) != null) {
            Package pack = (Package)(packages.get(i));
            tx = pack.getX();
            ty = pack.getY();
            if (inRange(ax,ay,tx,ty)) {
               VisiblePackage vpack;
  
               // modified 10/5/16 to add noise to the sensing...
               PacAgentRep arep = pack.getAgent(); 
               if (arep != null && arep.agent == agent) {	   // no noise if the agent is holding the package
            	   vpack = new VisiblePackage(pack);	
            	   heldPackage = vpack;
               } else {
            	   int destOffset = 0;
            	   double roll = PackageState.rand.nextDouble();      // test for chance of noise
            	   if (roll < LOCATION_NOISE) {
            		   int dx = PackageState.rand.nextInt(3) - 1;
            		   int dy = PackageState.rand.nextInt(3) - 1;
            		   vpack = new VisiblePackage(pack, pack.getDestX()+dx, pack.getDestY()+dy);
            		   System.out.println("*** LOCATION NOISE ****");
            		   System.out.println("Agent " + agent.getId() + ": Package(" + tx + "," + ty + ") " +
            				   "Real Loc: (" + pack.getDestX() + "," + pack.getDestY() + ") " +
            				   "Noise Loc: (" + vpack.getX() + "," + vpack.getY() + ")");
            		   System.out.println("**************");
            	   }
            	   else if (roll < DESTINATION_NOISE + LOCATION_NOISE &&
            			   state.getNumDestinations() > 1) {       // if no location noise, there's an additional chance of destination noise
            		   // note, if there is only one destination, there cannot be any noise in the target destination.
            		   destOffset = PackageState.rand.nextInt(state.getNumDestinations()- 1);
            		   int noiseDest = (pack.getDestId() + destOffset + 1) % state.getNumDestinations();
            		   Location fakeDestination = state.getDestinations()[noiseDest];
            		   vpack = new VisiblePackage(pack, fakeDestination);
            		   System.out.println("*** DESTINATION NOISE ****");
            		   System.out.println("Agent " + agent.getId() + ": Package(" + tx + "," + ty + ") " +
            				   "Real Dest: (" + pack.getDestX() + "," + pack.getDestY() + ") " +
            				   "Noise Dest: (" + fakeDestination.getX() + "," + fakeDestination.getY() + ")");
            		   System.out.println("**************");
            	   }
            	   else
            		   vpack = new VisiblePackage(pack);
               }
               visPackages.add(vpack);
            }
         }
      }
      
      // add in noise for "phantom packages" (i.e., packages that aren't really there
      double roll = PackageState.rand.nextDouble();      // test for chance of noise
	  if (roll < PHANTOM_PACKAGE_NOISE) {
		  int fakeId = PackageState.rand.nextInt(state.getOrigNumPackages());
		  int px = PackageState.rand.nextInt(VIS_RADIUS*2 + 1) - VIS_RADIUS + ax;
		  int py = PackageState.rand.nextInt(VIS_RADIUS*2 + 1) - VIS_RADIUS + ay;
		  int destId = PackageState.rand.nextInt(state.getNumDestinations());
		  Location fakeDestination = state.getDestinations()[destId];
		  VisiblePackage vpack = new VisiblePackage(fakeId, px, py, fakeDestination.getX(), fakeDestination.getY());
		  System.out.println("*** PHANTOM PACKAGE NOISE ****");
		  System.out.println("Agent " + agent.getId() + ": Illusory Package(" + px + "," + py + ") " +
				   "Illuosry Dest: (" + vpack.getDestX() + "," + vpack.getDestY() + ") ");
		  System.out.println("**************");
	  }
      
      messages = state.getMessages().clone();

      // determine bump
      if (state.bumped(agent))
         bump = true;
      else
         bump = false;

      // printPercept();
   }

   /** Returns true if the percept reflects that the agent bumped into
      an obstacle as a result of its most recent action. */
   public boolean feelBump() {
      return bump;
   }


   /** Returns the length of the world. Note, the world is a square,
      so this also doubles as its height. */
   public int getWorldSize() {
      return worldSize;
   }

   /** Returns an array of the visible agents. Each element is of type
      VisibleAgent. */
   public VisibleAgent[] getVisAgents() {
      return visAgents.toArray(new VisibleAgent[visAgents.size()]);
   }

   /** Return an array of the visible packages. Each element is of type
      VisiblePackage. Only packages that have not been delivered will
      appear. */
   public VisiblePackage[] getVisPackages() {
      return visPackages.toArray(new VisiblePackage[visPackages.size()]);
   }

   /** Returns an array of strings representing messages sent since the
      agent's last turn. There are only as many elements as messages
      sent, and there is no identification of which agent sent which
      message. */
   public String[] getMessages() {
      return messages;
   }

   /** Returns the visible package that the agent is currently holding.
    * If the agent is not holding a package, returns null. Note, since
    * the agent can see all packages in the 9x9 square centered on 
    * itself, this package is also returned by getVisPackages().
    * @return
    */
   public VisiblePackage getHeldPackage() {
      return heldPackage;
   }
   
   /* Print information about the percept to the console. Useful for
    * debugging.
    */
   public void printPercept() {
      System.out.println();
      System.out.println("Percept:");
      for (int i=0; i < visAgents.size(); i++)
         System.out.println(visAgents.get(i));
      for (int i=0; i < visPackages.size(); i++)
         System.out.println(visPackages.get(i));
      for (int i=0; i < messages.length; i++)
         System.out.println(messages[i]);
      System.out.println();
      System.out.println();
   }

   /* Given the x,y location of an agent and the x,y location of a
    * target, return true if the target is within sight range of
    * the agent. The agent can see everything in a square centered
    * on it with sides of size VIS_RADIUS*2+1.
    */
   protected boolean inRange(int ax, int ay, int tx, int ty) {
      return (tx >= ax-VIS_RADIUS) && (tx <= ax+VIS_RADIUS) && (ty >= ay-VIS_RADIUS) && (ty <= ay+VIS_RADIUS); 
   }
}
