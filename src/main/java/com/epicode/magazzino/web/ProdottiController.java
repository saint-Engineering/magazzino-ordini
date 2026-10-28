package com.epicode.magazzino.web;

import com.epicode.magazzino.model.MagazzinoException;
import com.epicode.magazzino.model.Riassortimento;
import com.epicode.magazzino.model.RichiestaNonValidaException;
import com.epicode.magazzino.model.Smaltimento;
import com.epicode.magazzino.service.CatalogoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProdottiController {

    private final CatalogoService catalogo;

    public ProdottiController(CatalogoService catalogo) {
        this.catalogo = catalogo;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/prodotti";
    }

    @GetMapping("/prodotti")
    public String elenco(Model model) {
        model.addAttribute("prodotti", catalogo.elenco());
        return "prodotti";
    }

    @GetMapping("/prodotti/nuovo")
    public String nuovo(Model model) {
        model.addAttribute("form", new ProdottoForm());
        return "prodotto-nuovo";
    }

    @PostMapping("/prodotti/nuovo")
    public String crea(@ModelAttribute("form") ProdottoForm form,
                       Model model, RedirectAttributes redirect) {
        try {
            if (form.getScortaMinima() == null) {
                throw new RichiestaNonValidaException("Indica la scorta minima");
            }
            catalogo.crea(form.getCodice(), form.getNome(), form.getPrezzo(),
                    form.getScortaMinima());
            redirect.addFlashAttribute("messaggio", "Prodotto censito");
            return "redirect:/prodotti";
        } catch (MagazzinoException e) {
            model.addAttribute("errore", e.getMessage());
            return "prodotto-nuovo";
        }
    }

    @GetMapping("/prodotti/{codice}/riassortimento")
    public String riassortimento(@PathVariable String codice, Model model) {
        model.addAttribute("prodotto", catalogo.trova(codice));
        model.addAttribute("form", new RiassortimentoForm());
        return "riassortimento";
    }

    @PostMapping("/prodotti/{codice}/riassortimento")
    public String riassortisci(@PathVariable String codice,
                               @ModelAttribute("form") RiassortimentoForm form,
                               Model model, RedirectAttributes redirect) {
        try {
            if (form.getQuantita() == null) {
                throw new RichiestaNonValidaException("Indica la quantità da riassortire");
            }
            catalogo.riassortisci(codice, new Riassortimento(form.getQuantita(), form.getNota()));
            redirect.addFlashAttribute("messaggio", "Riassortimento registrato");
            return "redirect:/movimenti";
        } catch (MagazzinoException e) {
            model.addAttribute("errore", e.getMessage());
            model.addAttribute("prodotto", catalogo.trova(codice));
            return "riassortimento";
        }
    }

    @GetMapping("/prodotti/{codice}/smaltimento")
    public String smaltimento(@PathVariable String codice, Model model) {
        model.addAttribute("prodotto", catalogo.trova(codice));
        model.addAttribute("form", new SmaltimentoForm());
        return "smaltimento";
    }

    @PostMapping("/prodotti/{codice}/smaltimento")
    public String smaltisci(@PathVariable String codice,
                            @ModelAttribute("form") SmaltimentoForm form,
                            Model model, RedirectAttributes redirect) {
        try {
            if (form.getQuantita() == null) {
                throw new RichiestaNonValidaException("Indica la quantità da smaltire");
            }
            catalogo.smaltisci(codice, new Smaltimento(form.getQuantita(), form.getNota()));
            redirect.addFlashAttribute("messaggio", "Smaltimento registrato");
            return "redirect:/movimenti";
        } catch (MagazzinoException e) {
            model.addAttribute("errore", e.getMessage());
            model.addAttribute("prodotto", catalogo.trova(codice));
            return "smaltimento";
        }
    }
}
