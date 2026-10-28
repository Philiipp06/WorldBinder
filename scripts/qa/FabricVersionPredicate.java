import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

/** Uses the actual supported Fabric Loader predicate implementation rather than guessing patch compatibility. */
class FabricVersionPredicate {
    public static void main(String[] args)throws Exception{
        var version=Version.parse(args[0]);
        for(int i=1;i<args.length;i++)if(VersionPredicate.parse(args[i]).test(version)){System.out.println("true");return;}
        System.out.println("false");
    }
}
