package com.pocsigmet.grib2;

import ucar.nc2.NetcdfFile;
import ucar.nc2.NetcdfFiles;
import ucar.nc2.Variable;
import ucar.ma2.Array;

public class TestNetCDFFixed {
    
    public static void main(String[] args) {
        System.out.println("🔧 === NetCDF-Java CORREÇÃO APLICADA ===");
        
        try {
            String gribFile = "data/grib2/gfs_2026032512_pgrb2full_0p50_f000.grib2";
            
            try (NetcdfFile ncfile = NetcdfFiles.open(gribFile)) {
                
                // 1. Listar TODAS as variáveis (aplicando os detalhes)
                System.out.println("\n1️⃣ Listando TODAS as variáveis:");
                int count = 0;
                for (Variable var : ncfile.getVariables()) {
                    if (count < 20) {
                        System.out.println("📄 " + var.getFullName() + 
                                         " - Shape: " + java.util.Arrays.toString(var.getShape()));
                    }
                    count++;
                }
                System.out.println("📊 Total: " + count + " variáveis");
                
                // 2. Procurar nomes corretos (u, v, UGRD, VGRD, u10, v10)
                System.out.println("\n2️⃣ Procurando nomes corretos:");
                String[] windNames = {"u", "v", "UGRD", "VGRD", "u10", "v10", "uwind", "vwind"};
                
                for (String name : windNames) {
                    Variable var = ncfile.findVariable(name);
                    if (var != null) {
                        System.out.println("✅ " + name + " - Shape: " + java.util.Arrays.toString(var.getShape()));
                        
                        // Usar copyTo1DJavaArray() como sugerido
                        try {
                            Array data = var.read();
                            float[] array = (float[]) data.copyTo1DJavaArray();
                            System.out.println("   📊 Pontos: " + array.length + ", Exemplo: " + array[0]);
                        } catch (Exception e) {
                            System.out.println("   ⚠️ Erro ao converter: " + e.getMessage());
                        }
                    }
                }
                
                // 3. Verificar coordenadas
                System.out.println("\n3️⃣ Verificando coordenadas:");
                Variable latVar = ncfile.findVariable("lat");
                Variable lonVar = ncfile.findVariable("lon");
                
                if (latVar != null && lonVar != null) {
                    Array latData = latVar.read();
                    Array lonData = lonVar.read();
                    
                    // Usar copyTo1DJavaArray()
                    float[] latArray = (float[]) latData.copyTo1DJavaArray();
                    float[] lonArray = (float[]) lonData.copyTo1DJavaArray();
                    
                    System.out.println("📍 Lat: " + latArray.length + " pontos (" + 
                                     latArray[0] + " a " + latArray[latArray.length-1] + ")");
                    System.out.println("📍 Lon: " + lonArray.length + " pontos (" + 
                                     lonArray[0] + " a " + lonArray[lonArray.length-1] + ")");
                    
                    // Converter Congonhas para coordenadas 0-360°E
                    double congonhasLat = -23.6267;
                    double congonhasLon = -46.6556 + 360; // -46.6°W → 313.4°E
                    
                    boolean latOk = congonhasLat >= Math.min(latArray[0], latArray[latArray.length-1]) && 
                                   congonhasLat <= Math.max(latArray[0], latArray[latArray.length-1]);
                    boolean lonOk = congonhasLon >= Math.min(lonArray[0], lonArray[lonArray.length-1]) && 
                                   congonhasLon <= Math.max(lonArray[0], lonArray[lonArray.length-1]);
                    
                    System.out.println("✈️ Congonhas na grade: " + (latOk && lonOk ? "✅ SIM" : "❌ NÃO"));
                    
                    if (latOk && lonOk) {
                        System.out.println("🎯 Congonhas está dentro da cobertura!");
                        
                        // Encontrar índices mais próximos
                        int latIndex = findNearestIndex(latArray, (float)congonhasLat);
                        int lonIndex = findNearestIndex(lonArray, (float)congonhasLon);
                        
                        System.out.println("📍 Ponto da grade mais próximo: " + 
                                         latArray[latIndex] + "°, " + lonArray[lonIndex] + "°");
                    }
                }
                
                // 4. Procurar variáveis com subset (read com origin/shape)
                System.out.println("\n4️⃣ Testando subset de dados:");
                testSubsetReading(ncfile);
                
            }
            
        } catch (Exception e) {
            System.err.println("❌ ERRO: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void testSubsetReading(NetcdfFile ncfile) {
        try {
            // Procurar primeira variável com dados grandes
            for (Variable var : ncfile.getVariables()) {
                if (var.getSize() > 1000) {
                    System.out.println("📊 Testando subset em: " + var.getFullName());
                    
                    int[] shape = var.getShape();
                    System.out.println("   Shape completa: " + java.util.Arrays.toString(shape));
                    
                    // Ler apenas uma pequena parte (origin=[0,0,...], shape=[min(10,size),...])
                    int[] origin = new int[shape.length];
                    int[] subsetShape = new int[shape.length];
                    
                    for (int i = 0; i < shape.length; i++) {
                        origin[i] = 0;
                        subsetShape[i] = Math.min(10, shape[i]);
                    }
                    
                    Array subset = var.read(origin, subsetShape);
                    float[] subsetArray = (float[]) subset.copyTo1DJavaArray();
                    
                    System.out.println("   Subset lido: " + subsetArray.length + " pontos");
                    System.out.println("   Exemplo: " + subsetArray[0]);
                    
                    break; // Testar apenas uma variável
                }
            }
            
        } catch (Exception e) {
            System.out.println("⚠️ Erro no subset: " + e.getMessage());
        }
    }
    
    private static int findNearestIndex(float[] array, float target) {
        int bestIndex = 0;
        float minDiff = Math.abs(array[0] - target);
        
        for (int i = 1; i < array.length; i++) {
            float diff = Math.abs(array[i] - target);
            if (diff < minDiff) {
                minDiff = diff;
                bestIndex = i;
            }
        }
        
        return bestIndex;
    }
}
