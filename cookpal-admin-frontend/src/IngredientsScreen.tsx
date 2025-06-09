import {DataGrid, GridToolbarContainer, GridToolbarQuickFilter} from '@mui/x-data-grid';
import {useEffect, useState} from 'react';
import RestAPI, {Ingredient} from './RestAPI';
import {Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField, Switch, FormControlLabel, Accordion, AccordionSummary, AccordionDetails} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';

interface IngredientAlternativeName {
  id: number;
  languageIsoCode: string;
  alternativeName: string; // Fix the field name to match the UI and backend expectations
}

const CustomToolbar = ({onAddIngredient}: {onAddIngredient: () => void}) => (
  <GridToolbarContainer>
    <Button variant="contained" onClick={onAddIngredient} style={{marginRight: '1rem'}}>
      Add Ingredient
    </Button>
    <GridToolbarQuickFilter debounceMs={500} /> {/* Add quick filter */}
  </GridToolbarContainer>
);

export const IngredientsScreen = () => {
  const [ingredients, setIngredients] = useState<Ingredient[]>();
  const [filteredIngredients, setFilteredIngredients] = useState<Ingredient[]>();
  const [searchQuery, setSearchQuery] = useState(''); // Add state for search query
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [isJsonMode, setIsJsonMode] = useState(false);
  const [jsonInput, setJsonInput] = useState('');
  const [formInput, setFormInput] = useState<Partial<Ingredient>>({
    name: '',
    alternativeNames: [{languageIsoCode: '', alternativeName: ''}],
    nutrientsEnergy: null,
    nutrientsFat: null,
    nutrientsSaturatedFat: null,
    nutrientsCarbohydrates: null,
    nutrientsSugar: null,
    nutrientsProtein: null,
    nutrientsSalt: null,
  });
  const [editingIngredient, setEditingIngredient] = useState<Ingredient | null>(null); // Track the ingredient being edited

  useEffect(() => {
    RestAPI.getAllIngredients().then((ingredients) => {
      setIngredients(ingredients);
      setFilteredIngredients(ingredients); // Initialize filtered ingredients
    });
  }, []);

  useEffect(() => {
    if (ingredients) {
      setFilteredIngredients(
          ingredients.filter((ingredient) =>
            ingredient.name?.toLowerCase().includes(searchQuery.toLowerCase()), // Add optional chaining to handle null/undefined
          ),
      );
    }
  }, [searchQuery, ingredients]); // Update filtered ingredients when search query changes

  const handleEditIngredient = (ingredient: Ingredient) => {
    setEditingIngredient(ingredient);
    setFormInput(ingredient); // Populate the form with the ingredient's data
    setIsDialogOpen(true);
  };

  const updateJson = async (newJson: string) => {
    setJsonInput(newJson);
    setFormInput(JSON.parse(newJson)); // Update form input with parsed JSON
  };

  const handleSaveIngredient = async () => {
    if (editingIngredient) {
      const updatedIngredient = await RestAPI.updateIngredient(formInput); // Update the ingredient
      setIngredients((prev) =>
        prev?.map((ingredient) =>
          ingredient.id === updatedIngredient.id ? updatedIngredient : ingredient,
        ),
      ); // Update the ingredients state
      setFilteredIngredients((prev) =>
        prev?.map((ingredient) =>
          ingredient.id === updatedIngredient.id ? updatedIngredient : ingredient,
        ),
      ); // Update the filtered ingredients state
    } else {
      const newIngredient = await RestAPI.addIngredient(formInput); // Add a new ingredient
      setIngredients((prev) => (prev ? [...prev, newIngredient] : [newIngredient])); // Add to ingredients state
      setFilteredIngredients((prev) => (prev ? [...prev, newIngredient] : [newIngredient])); // Add to filtered ingredients state
    }
    setIsDialogOpen(false);
    setEditingIngredient(null); // Reset editing state
  };

  const handleAddAlternativeName = () => {
    setFormInput({
      ...formInput,
      alternativeNames: [...(formInput.alternativeNames ?? []), {languageIsoCode: '', alternativeName: ''}],
    });
  };

  const handleAlternativeNameChange = (index: number, field: keyof IngredientAlternativeName, value: string) => {
    const updatedalternativeNames = [...(formInput.alternativeNames ?? [])];
    updatedalternativeNames[index] = {...updatedalternativeNames[index], [field]: value};
    setFormInput({...formInput, alternativeNames: updatedalternativeNames});
  };

  const handleRemoveAlternativeName = (index: number) => {
    const updatedalternativeNames = (formInput.alternativeNames ?? []).filter((_, i) => i !== index);
    setFormInput({...formInput, alternativeNames: updatedalternativeNames});
  };

  const jsonStructureExample = JSON.stringify(
      {
        name: 'Example Ingredient',
        alternativeNames: [{languageIsoCode: 'en', alternativeName: 'Example'}],
        nutrientsEnergy: 100, // Energy in kcal per 100g
        nutrientsFat: 10, // Fat in grams per 100g
        nutrientsSaturatedFat: 5, // Saturated fat in grams per 100g
        nutrientsCarbohydrates: 20, // Carbohydrates in grams per 100g
        nutrientsSugar: 10, // Sugar in grams per 100g
        nutrientsProtein: 5, // Protein in grams per 100g
        nutrientsSalt: 1, // Salt in grams per 100g
      },
      null,
      2,
  );

  const chatGptInstructions = `
You should add all basic ingredients an its nutrients to the database.
To generate ingredients JSON using ChatGPT, provide the following prompt:

Generate a JSON object for an ingredient with the following schema:
{
  name: string, // The name of the ingredient (e.g., 'Tomato').
  alternativeNames: Array<{ languageIsoCode: string, alternativeName: string }>, // A list of alternative names with language codes.
  nutrientsEnergy: number, // Energy in kcal per 100g.
  nutrientsFat: number, // Fat in grams per 100g.
  nutrientsSaturatedFat: number, // Saturated fat in grams per 100g.
  nutrientsCarbohydrates: number, // Carbohydrates in grams per 100g.
  nutrientsSugar: number, // Sugar in grams per 100g.
  nutrientsProtein: number, // Protein in grams per 100g.
  nutrientsSalt: number // Salt in grams per 100g.
}

For the 'alternativeNames' field:
- Generate multiple common alternative name in English (languageIsoCode: 'en').
- Generate multiple common alternative name in German (languageIsoCode: 'de').
- These alternative names are used to deduplicate ingredients if the user enters another name of the same thing

Ensure the JSON is valid and adheres to this schema. Provide real values for the fields. Only numbers, no ranges or something else. Only answer with json as your response is parsed by a machine.

Do this for this ingredient:

Potato
`;

  return (
    <div style={{height: '100vh', width: '100%', backgroundColor: 'white', overflow: 'hidden'}}>
      <DataGrid
        rows={filteredIngredients ?? []} // Use filtered ingredients
        columns={[
          {field: 'id', headerName: 'id', width: 70},
          {field: 'name', headerName: 'name', width: 300},
          {field: 'createdOn', headerName: 'createdOn', width: 200},
          {field: 'lastChange', headerName: 'lastChange', width: 200},
          {field: 'nutrientsEnergy', headerName: 'Energy (kcal)', width: 150},
          {field: 'nutrientsFat', headerName: 'Fat (g)', width: 150},
          {field: 'nutrientsCarbohydrates', headerName: 'Carbohydrates (g)', width: 150},
          {field: 'nutrientsProtein', headerName: 'Protein (g)', width: 150},
          {field: 'nutrientsSalt', headerName: 'Salt (g)', width: 150},
          {
            field: 'actions',
            headerName: 'Actions',
            width: 150,
            renderCell: (params) => (
              <Button
                variant="outlined"
                onClick={() => handleEditIngredient(params.row)}
              >
                Edit
              </Button>
            ),
          },
        ]}
        components={{
          Toolbar: () => <CustomToolbar onAddIngredient={() => setIsDialogOpen(true)} />,
        }}
        onFilterModelChange={(model) => {
          const filterValue = model.quickFilterValues?.[0] || '';
          setSearchQuery(filterValue);
        }}
        initialState={{
          pagination: {
            paginationModel: {page: 0, pageSize: 50},
          },
        }}
        pageSizeOptions={[50, 100, 500, 1000]}
        checkboxSelection
      />

      <Dialog open={isDialogOpen} onClose={() => setIsDialogOpen(false)}>
        <DialogTitle>{editingIngredient ? 'Edit Ingredient' : 'Add Ingredient'}</DialogTitle>
        <DialogContent>
          <FormControlLabel
            control={<Switch checked={isJsonMode} onChange={() => setIsJsonMode(!isJsonMode)} />}
            label="JSON Mode"
          />
          {isJsonMode ? (
            <>
              <TextField
                label="JSON Input"
                multiline
                rows={6}
                fullWidth
                value={jsonInput}
                onChange={(e) => updateJson(e.target.value)}
              />
              <Accordion>
                <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                  Show JSON Structure Example and ChatGPT Instructions
                </AccordionSummary>
                <AccordionDetails>
                  <div>
                    <h4>JSON Structure Example:</h4>
                    <pre
                      style={{
                        backgroundColor: '#f5f5f5',
                        padding: '1rem',
                        borderRadius: '4px',
                        overflowX: 'auto',
                      }}
                    >
                      {jsonStructureExample}
                    </pre>
                    <h4>ChatGPT Instructions:</h4>
                    <pre
                      style={{
                        backgroundColor: '#f5f5f5',
                        padding: '1rem',
                        borderRadius: '4px',
                        overflowX: 'auto',
                      }}
                    >
                      {chatGptInstructions}
                    </pre>
                  </div>
                </AccordionDetails>
              </Accordion>
            </>
          ) : (
            <>
              <TextField
                label="Name"
                fullWidth
                margin="normal"
                value={formInput.name}
                onChange={(e) => setFormInput({...formInput, name: e.target.value})}
              />
              <TextField
                label="Energy (kcal)"
                type="number"
                fullWidth
                margin="normal"
                value={formInput.nutrientsEnergy ?? ''}
                onChange={(e) => setFormInput({...formInput, nutrientsEnergy: e.target.value ? parseFloat(e.target.value) : null})}
              />
              <TextField
                label="Fat (g)"
                type="number"
                fullWidth
                margin="normal"
                value={formInput.nutrientsFat ?? ''}
                onChange={(e) => setFormInput({...formInput, nutrientsFat: e.target.value ? parseFloat(e.target.value) : null})}
              />
              <TextField
                label="Saturated Fat (g)"
                type="number"
                fullWidth
                margin="normal"
                value={formInput.nutrientsSaturatedFat ?? ''}
                onChange={(e) => setFormInput({...formInput, nutrientsSaturatedFat: e.target.value ? parseFloat(e.target.value) : null})}
              />
              <TextField
                label="Carbohydrates (g)"
                type="number"
                fullWidth
                margin="normal"
                value={formInput.nutrientsCarbohydrates ?? ''}
                onChange={(e) => setFormInput({...formInput, nutrientsCarbohydrates: e.target.value ? parseFloat(e.target.value) : null})}
              />
              <TextField
                label="Sugar (g)"
                type="number"
                fullWidth
                margin="normal"
                value={formInput.nutrientsSugar ?? ''}
                onChange={(e) => setFormInput({...formInput, nutrientsSugar: e.target.value ? parseFloat(e.target.value) : null})}
              />
              <TextField
                label="Protein (g)"
                type="number"
                fullWidth
                margin="normal"
                value={formInput.nutrientsProtein ?? ''}
                onChange={(e) => setFormInput({...formInput, nutrientsProtein: e.target.value ? parseFloat(e.target.value) : null})}
              />
              <TextField
                label="Salt (g)"
                type="number"
                fullWidth
                margin="normal"
                value={formInput.nutrientsSalt ?? ''}
                onChange={(e) => setFormInput({...formInput, nutrientsSalt: e.target.value ? parseFloat(e.target.value) : null})}
              />
              {formInput.alternativeNames?.map((altName, index) => (
                <div key={index} style={{marginBottom: '1rem'}}>
                  <TextField
                    label="Language ISO Code"
                    fullWidth
                    margin="normal"
                    value={altName.languageIsoCode}
                    onChange={(e) =>
                      handleAlternativeNameChange(index, 'languageIsoCode', e.target.value)
                    }
                  />
                  <TextField
                    label="Alternative Name"
                    fullWidth
                    margin="normal"
                    value={altName.alternativeName} // Fix the value to correctly bind to `alternativeName`
                    onChange={(e) =>
                      handleAlternativeNameChange(index, 'alternativeName', e.target.value) // Fix the field name to `alternativeName`
                    }
                  />
                  <Button
                    variant="outlined"
                    color="secondary"
                    onClick={() => handleRemoveAlternativeName(index)}
                  >
                    Remove
                  </Button>
                </div>
              ))}
              <Button onClick={handleAddAlternativeName}>Add Alternative Name</Button>
            </>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setIsDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleSaveIngredient}>
            {editingIngredient ? 'Save' : 'Add'}
          </Button>
        </DialogActions>
      </Dialog>
    </div>
  );
};
