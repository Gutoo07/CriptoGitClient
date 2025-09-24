package fateczl.CriptoGitClient.model;

import java.util.List;
import java.util.ArrayList;

public class Tree {
    private String hash;
    private String name;
    private List<Arquivo> arquivos;
    private List<Tree> trees;

    public Tree() {
        this.arquivos = new ArrayList<>();
        this.trees = new ArrayList<>();
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setArquivos(List<Arquivo> arquivos) {
        this.arquivos = arquivos;
    }

    public void setTrees(List<Tree> trees) {
        this.trees = trees;
    }

    public String getHash() {
        return hash;
    }

    public String getName() {
        return name;
    }

    public List<Arquivo> getArquivos() {
        return arquivos;
    }

    public List<Tree> getTrees() {
        return trees;
    }

    public void addArquivo(Arquivo arquivo) {
        arquivos.add(arquivo);
    }

    public void addTree(Tree tree) {
        trees.add(tree);
    }
}
