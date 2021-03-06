package it.diamonds.grid;


import it.diamonds.ScoreCalculator;
import it.diamonds.StoneCalculator;
import it.diamonds.droppable.CrushPriority;
import it.diamonds.droppable.Droppable;
import it.diamonds.droppable.DroppableList;
import it.diamonds.droppable.pair.Direction;
import it.diamonds.engine.Config;
import it.diamonds.engine.Environment;
import it.diamonds.grid.iteration.CreateNewBigGems;
import it.diamonds.grid.iteration.DroppableIteration;
import it.diamonds.grid.iteration.ExtendIteration;
import it.diamonds.grid.iteration.MergeBigGemsIteration;


public final class Grid
{
    private DroppableList droppableList = new DroppableList();

    private int actualGravity;

    private int normalGravity;

    private int gravityMultiplier;

    private int strongestGravityMultiplier;

    private int numberOfRowsInGrid;

    private int numberOfColumnsInGrid;

    private CreateNewBigGems createNewBigGemsIteration;

    private CrushPriority[] crushPriorities = {
        CrushPriority.ABSOLUTE_PRIORITY, CrushPriority.NORMAL_PRIORITY };


    public Grid(Environment environment)
    {
        numberOfRowsInGrid = environment.getConfig().getInteger("rows");
        numberOfColumnsInGrid = environment.getConfig().getInteger("columns");

        initializeGravity(environment.getConfig());

        createNewBigGemsIteration = new CreateNewBigGems(this, environment.getEngine());
    }


    private void initializeGravity(Config config)
    {
        normalGravity = config.getInteger("NormalGravity");
        gravityMultiplier = config.getInteger("GravityMultiplier");
        strongestGravityMultiplier = config.getInteger("StrongestGravityMultiplier");
        actualGravity = normalGravity;
    }


    public void setStrongestGravity()
    {
        actualGravity = normalGravity * strongestGravityMultiplier;
    }


    public void setStrongerGravity()
    {
        actualGravity = normalGravity * gravityMultiplier;
    }


    public void setNormalGravity()
    {
        actualGravity = normalGravity;
    }


    public void setGravity(int gravity)
    {
        actualGravity = gravity;
    }


    public int getActualGravity()
    {
        return actualGravity;
    }


    public int getHeight()
    {
        return Cell.SIZE_IN_PIXELS * numberOfRowsInGrid;
    }


    public float getRowUpperBound(int row)
    {
        return row * Cell.SIZE_IN_PIXELS;
    }


    public boolean isCellFree(Cell cell)
    {
        if (!isValidCell(cell))
        {
            return false;
        }

        final boolean isCellFree = (getDroppableAt(cell) == null);

        return isCellFree;
    }


    public boolean isValidCell(Cell cell)
    {
        if (cell == null)
        {
            return false;
        }

        if (cell.getColumn() >= numberOfColumnsInGrid)
        {
            return false;
        }

        return (cell.getRow() < numberOfRowsInGrid);
    }


    public Droppable getDroppableAt(Cell cell)
    {
        for (Droppable droppable : droppableList)
        {
            if (droppable.getRegion().containsCell(cell))
            {
                return droppable;
            }
        }

        return null;
    }


    public DroppableList getDroppablesInArea(Region region)
    {
        DroppableList list = new DroppableList();

        for (int column = region.getLeftColumn(); column <= region.getRightColumn(); column++)
        {
            for (int row = region.getTopRow(); row <= region.getBottomRow(); row++)
            {
                Cell cell = Cell.create(row, column);
                Droppable droppable = getDroppableAt(cell);

                if (droppable != null && !list.contains(droppable))
                {
                    list.add(droppable);
                }
            }
        }

        return list;
    }


    public void insertDroppable(Droppable droppable)
    {
        if (droppable == null)
        {
            throw new IllegalArgumentException();
        }

        Cell cell = Cell.create(droppable.getRegion().getTopRow(), droppable.getRegion().getLeftColumn());
        if (!isCellFree(cell))
        {
            throw new IllegalArgumentException();
        }
        
        droppable.moveToCell(cell);

        droppableList.add(droppable);
    }


    public void removeDroppable(Droppable droppable)
    {
        droppableList.remove(droppable);
    }


    public boolean droppableCanMove(Droppable droppable, Direction direction)
    {
        if (droppable == null)
        {
            return false;
        }

        Region region = droppable.getRegion();

        int destRow = region.getTopRow() + direction.getRowDelta();
        int destColumn = region.getLeftColumn() + direction.getColumnDelta();

        return isCellFree(Cell.create(destRow, destColumn));
    }


    public void translateDroppable(Droppable droppable, Direction direction)
    {
        if (droppable == null)
        {
            throw new IllegalArgumentException();
        }

        Region region = droppable.getRegion();

        int row = region.getTopRow() + direction.getRowDelta();
        int column = region.getLeftColumn() + direction.getColumnDelta();
        moveDroppableToCell(droppable, Cell.create(row, column));
    }


    public void moveDroppableToCell(Droppable droppable, Cell cell)
    {
        if (!isCellFree(cell))
        {
            return;
        }
        
        Region region = droppable.getRegion();
        
        int topRow = region.getTopRow();
        
        float heightInCell = droppable.getPositionInGridLocalSpace().getY() - (topRow * Cell.SIZE_IN_PIXELS);
        
        droppable.moveToCell(cell);
        
        float relativeHeightAfterTranslation = droppable.getPositionInGridLocalSpace().getY();
        droppable.getPositionInGridLocalSpace().setY(relativeHeightAfterTranslation + heightInCell);
    }


    public void updateDroppable(Droppable droppable)
    {

        if (!droppable.isFalling())
        {
            return;
        }

        droppable.moveDown(this);
    }


    public void updateDroppableAnimations(long timer)
    {
        for (Droppable droppable : droppableList)
        {
            droppable.update(timer);
        }
    }


    public int getNumberOfDroppables()
    {
        return droppableList.size();
    }


    public boolean isColumnFull(int column)
    {
        Cell cell = Cell.create(0, column);

        if (isCellFree(cell))
        {
            return false;
        }

        return !getDroppableAt(cell).isFalling();
    }


    public void updateBigGems()
    {
        runIteration(createNewBigGemsIteration);

        runIteration(new ExtendIteration(this));

        while (updateMergeAllBigGem())
        {
        }
    }


    private boolean updateMergeAllBigGem()
    {
        MergeBigGemsIteration mergeBigGemsIteration = new MergeBigGemsIteration(this);
        runIteration(mergeBigGemsIteration);

        return mergeBigGemsIteration.hasNotYetFinishedMerging();
    }


    public int getNumberOfRows()
    {
        return numberOfRowsInGrid;
    }


    public int getNumberOfColumns()
    {
        return numberOfColumnsInGrid;
    }


    public void updateCrushes(ScoreCalculator scoreCalculator, StoneCalculator stoneCalculator)
    {
        for (CrushPriority priority : crushPriorities)
        {
            for (Droppable droppable : new DroppableList(droppableList))
            {
                droppable.startCrush(this, priority, scoreCalculator, stoneCalculator);
            }
        }

        scoreCalculator.closeCrush();
    }


    public void updateStones()
    {
        for (Droppable droppable : droppableList)
        {
            droppable.updateTransformation();
        }
    }


    public int getHeightOfColumn(int column)
    {
        for (int row = 0; row < numberOfRowsInGrid; ++row)
        {
            Cell cell = Cell.create(row, column);
            if ((!isCellFree(cell)) && !getDroppableAt(cell).isFalling())
            {
                return numberOfRowsInGrid - row;
            }
        }

        return 0;
    }


    public void runIteration(DroppableIteration iteration)
    {
        for (Droppable droppable : new DroppableList(droppableList))
        {
            iteration.executeOn(droppable);
        }
    }


    public void updateFalls()
    {
        for (Droppable droppable : droppableList)
        {
            droppable.moveDown(this);
        }
    }
}
