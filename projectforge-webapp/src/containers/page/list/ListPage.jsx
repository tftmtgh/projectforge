import PropTypes from 'prop-types';
import React from 'react';
import DynamicLayout from '../../../components/base/dynamicLayout';
import { registerComponent } from '../../../components/base/dynamicLayout/components/DynamicRenderer';
import DynamicCheckbox
    from '../../../components/base/dynamicLayout/components/input/DynamicCheckbox';
import LoadingContainer from '../../../components/design/loading-container';
import { getObjectFromQuery, getServiceURL, handleHTTPErrors } from '../../../utilities/rest';
import { defaultValues as defaultContextValues, ListPageContext } from './ListPageContext';
import SearchFilter from './SearchFilter';
import SearchFilterCheckbox from './SearchFilterCheckbox';

function ListPage(
    {
        match,
        location,
    },
) {
    const [ui, setUI] = React.useState({ translations: {} });
    const [data, setData] = React.useState({});
    const [filter, setFilter] = React.useState({
        entries: [],
        extended: {},
    });
    const [filterFavorites, setFilterFavorites] = React.useState([]);
    const [variables, setVariables] = React.useState({});
    const [loading, setLoading] = React.useState(false);
    const [error, setError] = React.useState(undefined);

    const filterHelper = React.useMemo(() => ({
        addEntry: entry => setFilter({
            ...filter,
            entries: [
                ...filter.entries,
                entry,
            ],
        }),
        removeEntry: fieldOrValue => setFilter({
            ...filter,
            entries: filter.entries.filter(
                currentEntry => (currentEntry.field || currentEntry.value.value) !== fieldOrValue,
            ),
        }),
        editEntry: (id, newValue) => setFilter({
            ...filter,
            entries: filter.entries.map((currentEntry) => {
                if (currentEntry.field !== id) {
                    return currentEntry;
                }

                return {
                    ...currentEntry,
                    field: id,
                    value: newValue,
                };
            }),
        }),
        clearEntries: () => setFilter({
            ...filter,
            entries: [],
        }),
        setFilter: (id, value) => setFilter({
            ...filter,
            [id]: value,
        }),
        setExtended: (id, value) => setFilter({
            ...filter,
            extended: {
                ...filter.extended,
                [id]: value,
            },
        }),
        setFilterState: setFilter,
        sort: (column, sortProperty) => {
            let newSortProperty = sortProperty;

            if (!sortProperty) {
                newSortProperty = {
                    property: column,
                    sortOrder: 'ASCENDING',
                };
            } else if (sortProperty.sortOrder === 'DESCENDING') {
                newSortProperty = undefined;
            } else {
                newSortProperty.sortOrder = 'DESCENDING';
            }

            setFilter({
                ...filter,
                sortProperties: [
                    newSortProperty,
                    ...filter.sortProperties
                        .filter(entry => entry.property !== column)
                        .slice(0, 2),
                ]
                    .filter(entry => entry !== undefined),
            });
        },
    }), [filter]);

    // Register DynamicFilterCheckbox only for the ListPage
    // Attention: Can't use DynamicLayout twice here. Because the normal checkbox got an override.
    React.useEffect(() => {
        registerComponent('CHECKBOX', SearchFilterCheckbox);

        // Re-Register the DynamicCheckbox component on unmount.
        return () => registerComponent('CHECKBOX', DynamicCheckbox);
    }, []);

    const loadInitialList = () => {
        setLoading(true);
        setError(undefined);
        fetch(
            getServiceURL(
                `${match.params.category}/initialList`,
                getObjectFromQuery(location.search || ''),
            ),
            {
                method: 'GET',
                credentials: 'include',
            },
        )
            .then(handleHTTPErrors)
            .then(response => response.json())
            .then((
                {
                    ui: responseUi,
                    data: responseData,
                    filter: responseFilter,
                    filterFavorites: responseFilterFavorites,
                    variables: responseVariables,
                },
            ) => {
                setFilter(responseFilter);
                setFilterFavorites(responseFilterFavorites);
                setData(responseData);
                setUI(responseUi);
                setVariables(responseVariables);
                setLoading(false);
            })
            .catch(setError);
    };

    const performListUpdate = () => {
        if (loading) {
            return;
        }

        setError(undefined);
        setLoading(true);

        fetch(
            getServiceURL(`${match.params.category}/list`),
            {
                method: 'POST',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(filter),
            },
        )
            .then((response) => {
                setLoading(false);
                return response;
            })
            .then(handleHTTPErrors)
            .then(response => response.json())
            .then(setData)
            .catch(setError);
    };

    const performReset = () => {
        if (loading) {
            return;
        }

        setLoading(true);
        setError(undefined);

        fetch(
            getServiceURL(`${match.params.category}/filterReset`),
            {
                method: 'GET',
                credentials: 'include',
            },
        )
            .then(handleHTTPErrors)
            .then(response => response.json())
            .then((responseFilter) => {
                setFilter(responseFilter);
                setLoading(false);
            })
            .catch(setError);
    };

    const callAction = (action) => {
        switch (action.id) {
            case 'search':
                performListUpdate();
                break;
            case 'reset':
                performReset();
                break;
            default:
                throw Error(`Action ${action.id} not implemented.`);
        }
    };

    // Only reload the list when the category or search string changes.
    React.useEffect(loadInitialList, [match.params.category, location.search]);

    // Update the list on new sorting
    React.useEffect(() => {
        if (loading || !filter.sortProperties) {
            return;
        }

        performListUpdate();
    }, [filter.sortProperties]);

    if (error) {
        return <h4>{error.message}</h4>;
    }

    return (
        <ListPageContext.Provider
            value={{
                ...defaultContextValues,
                category: match.params.category,
                filter,
                filterFavorites,
                filterHelper,
                highlightRow: location.state ? location.state.id : -1,
                setFilterFavorites,
                setUI,
            }}
        >
            <LoadingContainer loading={loading}>
                <DynamicLayout
                    callAction={callAction}
                    ui={ui}
                    data={data}
                    setData={setData}
                    options={{
                        displayPageMenu: true,
                        disableLayoutRendering: loading,
                        setBrowserTitle: true,
                        showActionButtons: false,
                    }}
                    variables={variables}
                >
                    <SearchFilter />
                </DynamicLayout>
            </LoadingContainer>
        </ListPageContext.Provider>
    );
}

ListPage.propTypes = {
    location: PropTypes.shape({
        search: PropTypes.string,
        state: PropTypes.shape({
            id: PropTypes.number,
        }),
    }).isRequired,
    match: PropTypes.shape({
        params: PropTypes.shape({
            category: PropTypes.string.isRequired,
        }).isRequired,
    }).isRequired,
};

ListPage.defaultProps = {};

export default ListPage;
